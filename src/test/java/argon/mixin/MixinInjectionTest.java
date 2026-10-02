package argon.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Checks every injector in Argon's mixins against the Minecraft bytecode it targets.
 *
 * <p>Mixin descriptors, {@code @At} call sites and {@code @Local} captures are all resolved when the
 * game loads, never when the mod builds, and every target here is written as a string, so javac
 * cannot see a stale one either. A wrong descriptor or a capture that does not resolve is a crash the
 * first time a jigsaw structure places a child, long after the build said everything was fine. These
 * tests read the compiled mixin classes and the real Minecraft class files and answer the three
 * questions a mixin has to get right: does the target exist, is the call site there as often as the
 * injector demands, and does every captured local name exactly one variable.
 */
class MixinInjectionTest {
    private static final String MIXIN_PACKAGE = "argon.mixin";
    private static final String MIXIN_PATH = "argon/mixin/";
    private static final String MIXIN_CONFIG = "argon.mixins.json";
    private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String LOCAL_ANNOTATION = "Lcom/llamalad7/mixinextras/sugar/Local;";
    private static final List<String> INJECTOR_ANNOTATIONS = List.of(
        "Lorg/spongepowered/asm/mixin/injection/Inject;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyExpressionValue;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyReturnValue;",
        "Lorg/spongepowered/asm/mixin/injection/Redirect;",
        "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
        "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;",
        "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;"
    );
    private static final Map<String, String> LOCAL_REF_TYPES = Map.ofEntries(
        Map.entry("Lcom/llamalad7/mixinextras/sugar/ref/LocalBooleanRef;", "Z"),
        Map.entry("Lcom/llamalad7/mixinextras/sugar/ref/LocalByteRef;", "B"),
        Map.entry("Lcom/llamalad7/mixinextras/sugar/ref/LocalCharRef;", "C"),
        Map.entry("Lcom/llamalad7/mixinextras/sugar/ref/LocalShortRef;", "S"),
        Map.entry("Lcom/llamalad7/mixinextras/sugar/ref/LocalIntRef;", "I"),
        Map.entry("Lcom/llamalad7/mixinextras/sugar/ref/LocalLongRef;", "J"),
        Map.entry("Lcom/llamalad7/mixinextras/sugar/ref/LocalFloatRef;", "F"),
        Map.entry("Lcom/llamalad7/mixinextras/sugar/ref/LocalDoubleRef;", "D"),
        Map.entry("Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;", "Ljava/lang/Object;")
    );

    private record Injection(String injector, String where, MethodNode handler, String selector, int require,
                             List<AnnotationNode> at, Map<Integer, List<AnnotationNode>> captures) {
    }

    private record Local(String name, String desc, int index) {
    }

    private record MethodInfo(String name, String desc, int access, List<Local> locals, Map<String, Integer> calls) {
        List<Local> localsOfType(String desc) {
            return locals.stream().filter(local -> desc.equals(local.desc())).toList();
        }

        List<Local> argumentLocals(String desc) {
            int slots = argumentSlots();
            return localsOfType(desc).stream().filter(local -> local.index() < slots).toList();
        }

        int argumentSlots() {
            int slots = (access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
            for (Type argument : Type.getArgumentTypes(desc)) {
                slots += argument.getSize();
            }
            return slots;
        }
    }

    private record Mixin(String name, List<String> owners, List<Injection> injections) {
    }

    private record Resolved(Mixin mixin, String owner, Injection injection, MethodInfo method) {
    }

    @Test
    @DisplayName("every mixin target class and every target method exists in the Minecraft jar")
    void targetsResolve() {
        List<String> problems = new ArrayList<>();
        for (Mixin mixin : mixins()) {
            for (String owner : mixin.owners()) {
                Map<String, MethodInfo> methods = methods(owner);
                for (Injection injection : mixin.injections()) {
                    List<MethodInfo> matches = matches(methods, injection);
                    if (matches.isEmpty()) {
                        problems.add(describe(mixin, owner, injection) + " does not exist in " + owner);
                    } else if (matches.size() > 1) {
                        problems.add(describe(mixin, owner, injection) + " is ambiguous in " + owner + ", "
                            + matches.size() + " overloads go by that name");
                    }
                }
            }
        }
        report(problems);
    }

    @Test
    @DisplayName("every wrapped or injected call site is in the bytecode as often as it is required")
    void injectionPointsResolve() {
        List<String> problems = new ArrayList<>();
        for (Resolved resolved : resolved()) {
            MethodInfo method = resolved.method();
            Injection injection = resolved.injection();
            for (AnnotationNode at : injection.at()) {
                if (!"INVOKE".equals(string(at, "value"))) {
                    continue;
                }
                String reference = string(at, "target");
                if (reference == null || reference.isEmpty()) {
                    problems.add(describe(resolved) + " wraps INVOKE without naming a call");
                    continue;
                }
                int matches = callsOf(method, reference);
                if (matches < 1) {
                    problems.add(describe(resolved) + " wraps a call that is not in " + resolved.owner() + "."
                        + method.name() + ": " + reference);
                } else if (annotationInt(at, "ordinal") >= 0 || value(at, "slice") != null) {
                    continue;
                } else if (matches != injection.require()) {
                    problems.add(describe(resolved) + " requires " + injection.require() + " call site(s) but "
                        + reference + " appears " + matches + " time(s) in " + resolved.owner() + "."
                        + method.name());
                }
            }
        }
        report(problems);
    }

    @Test
    @DisplayName("every captured local resolves to exactly one variable, never an ambiguous one")
    void localCapturesResolve() {
        List<String> problems = new ArrayList<>();
        for (Resolved resolved : resolved()) {
            Injection injection = resolved.injection();
            Type[] parameters = Type.getArgumentTypes(injection.handler().desc);
            for (Map.Entry<Integer, List<AnnotationNode>> entry : injection.captures().entrySet()) {
                int index = entry.getKey();
                assertTrue(index >= 0 && index < parameters.length,
                    injection.where() + " annotates parameter " + index
                        + ", which its own descriptor does not have");
                for (AnnotationNode local : entry.getValue()) {
                    if (LOCAL_ANNOTATION.equals(local.desc)) {
                        checkLocalCapture(resolved, parameters[index].getDescriptor(), local, problems);
                    }
                }
            }
        }
        report(problems);
    }

    @Test
    @DisplayName("the mixin config lists exactly the mixin classes that exist")
    void mixinConfigIsComplete() {
        List<String> listed = listedMixins();
        assertFalse(listed.isEmpty(), MIXIN_CONFIG + " lists nothing");

        List<String> onDisk = new ArrayList<>();
        for (File file : mixinClassFiles()) {
            onDisk.add(MIXIN_PACKAGE + "." + file.getName().replace(".class", ""));
        }

        List<String> problems = new ArrayList<>();
        for (String name : onDisk) {
            if (!listed.contains(name)) {
                problems.add(name + " exists but " + MIXIN_CONFIG + " does not list it");
            }
        }
        for (String name : listed) {
            if (!onDisk.contains(name)) {
                problems.add(name + " is listed in " + MIXIN_CONFIG + " but there is no such class");
            }
        }
        report(problems);
    }

    private static void checkLocalCapture(Resolved resolved, String parameterDesc, AnnotationNode local,
                                          List<String> problems) {
        String where = describe(resolved) + " captures " + parameterDesc;
        String wanted = LOCAL_REF_TYPES.getOrDefault(parameterDesc, parameterDesc);
        assertTrue(wanted.startsWith("L") && wanted.endsWith(";"),
            where + " is a primitive capture, which no @Local supports");

        MethodInfo method = resolved.method();
        List<String> names = strings(local, "name");
        if (!names.isEmpty()) {
            for (String name : names) {
                List<Local> byName = method.locals().stream()
                    .filter(candidate -> name.equals(candidate.name())).toList();
                if (byName.isEmpty()) {
                    problems.add(where + " by name \"" + name + "\", which " + resolved.owner() + "."
                        + method.name() + " has no local of that name for");
                } else if (byName.stream().noneMatch(candidate -> wanted.equals(candidate.desc()))) {
                    problems.add(where + " by name \"" + name + "\", which is a " + byName.get(0).desc()
                        + " local rather than a " + wanted);
                }
            }
            return;
        }

        int ordinal = annotationInt(local, "ordinal");
        if (ordinal >= 0) {
            List<Local> candidates = annotationBoolean(local, "argsOnly")
                ? method.argumentLocals(wanted)
                : method.localsOfType(wanted);
            if (candidates.size() <= ordinal) {
                problems.add(where + " at ordinal " + ordinal + ", but only " + candidates.size() + " "
                    + wanted + " local(s) are in scope for " + resolved.owner() + "." + method.name());
            }
            return;
        }

        int index = annotationInt(local, "index");
        if (index >= 0) {
            if (method.localsOfType(wanted).stream().noneMatch(candidate -> candidate.index() == index)) {
                problems.add(where + " at index " + index + ", which is not a " + wanted + " local in "
                    + resolved.owner() + "." + method.name());
            }
            return;
        }

        // Implicit mode: a capture with no name, ordinal or index is resolved by type alone, and
        // MixinExtras rejects the injection unless exactly one variable of that type is in scope.
        // More than one in scope is a crash waiting for the first structure that reaches this code.
        List<Local> ofType = method.localsOfType(wanted);
        if (ofType.size() != 1) {
            problems.add(where + " implicitly, but " + resolved.owner() + "." + method.name() + " keeps "
                + ofType.size() + " locals of that type in scope (" + names(ofType)
                + "). Name the one this needs.");
        }
    }

    private static void report(List<String> problems) {
        if (!problems.isEmpty()) {
            fail(String.join(System.lineSeparator(), problems));
        }
    }

    private static List<Resolved> resolved() {
        List<Resolved> resolved = new ArrayList<>();
        for (Mixin mixin : mixins()) {
            for (String owner : mixin.owners()) {
                Map<String, MethodInfo> methods = methods(owner);
                for (Injection injection : mixin.injections()) {
                    List<MethodInfo> matches = matches(methods, injection);
                    if (matches.size() == 1) {
                        resolved.add(new Resolved(mixin, owner, injection, matches.get(0)));
                    }
                }
            }
        }
        return resolved;
    }

    private static List<MethodInfo> matches(Map<String, MethodInfo> methods, Injection injection) {
        String spec = injection.selector().trim();
        assertFalse(spec.indexOf('*') >= 0,
            injection.where() + " uses the wildcard method selector \"" + spec
                + "\", which this check does not follow");
        int parenthesis = spec.indexOf('(');
        String name = parenthesis < 0 ? spec : spec.substring(0, parenthesis);
        String desc = parenthesis < 0 ? null : spec.substring(parenthesis);
        List<MethodInfo> matches = new ArrayList<>();
        for (MethodInfo method : methods.values()) {
            if (method.name().equals(name) && (desc == null || method.desc().equals(desc))) {
                matches.add(method);
            }
        }
        return matches;
    }

    private static int callsOf(MethodInfo method, String reference) {
        int semicolon = reference.indexOf(';');
        assertTrue(semicolon > 1, "call reference \"" + reference + "\" names no owner");
        String name = reference.substring(semicolon + 1);
        int parenthesis = name.indexOf('(');
        assertTrue(parenthesis > 0, "call reference \"" + reference + "\" names no method");
        return method.calls().getOrDefault(reference.substring(1, semicolon) + "." + name, 0);
    }

    private static List<String> names(List<Local> locals) {
        return locals.stream().map(local -> local.name() + " (slot " + local.index() + ")").toList();
    }

    private static String describe(Mixin mixin, String owner, Injection injection) {
        return injection.injector() + " on " + injection.where() + " -> " + owner + "." + injection.selector();
    }

    private static String describe(Resolved resolved) {
        return resolved.injection().injector() + " on " + resolved.injection().where() + " -> "
            + resolved.owner() + "." + resolved.injection().selector();
    }

    private static List<Mixin> mixins() {
        List<Mixin> mixins = new ArrayList<>();
        for (File file : mixinClassFiles()) {
            ClassNode node = new ClassNode();
            new ClassReader(readFile(file)).accept(node, 0);
            String name = node.name.replace('/', '.');
            AnnotationNode mixin = annotation(allAnnotations(node.visibleAnnotations, node.invisibleAnnotations),
                MIXIN_ANNOTATION);
            assertNotNull(mixin, name + " has no @Mixin annotation, so it is not a mixin");
            mixins.add(new Mixin(name, owners(mixin), injections(node)));
        }
        assertFalse(mixins.isEmpty(), "no mixin classes were found");
        return mixins;
    }

    private static List<String> owners(AnnotationNode mixin) {
        List<String> owners = new ArrayList<>();
        for (Object value : values(mixin, "value")) {
            owners.add(Type.getType((String) value).getInternalName());
        }
        for (Object value : values(mixin, "targets")) {
            owners.add(((String) value).replace('.', '/'));
        }
        assertFalse(owners.isEmpty(), "@Mixin names no target");
        return owners;
    }

    private static List<Injection> injections(ClassNode mixin) {
        Map<String, Map<Integer, List<AnnotationNode>>> captures = new HashMap<>();
        for (MethodNode method : mixin.methods) {
            Map<Integer, List<AnnotationNode>> held = captures(method);
            if (!held.isEmpty()) {
                captures.put(method.name + method.desc, held);
            }
        }

        List<Injection> injections = new ArrayList<>();
        for (MethodNode method : mixin.methods) {
            if (method.name.startsWith("<") || method.instructions == null) {
                continue;
            }
            for (AnnotationNode injector : allAnnotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                if (!INJECTOR_ANNOTATIONS.contains(injector.desc)) {
                    continue;
                }
                int require = annotationInt(injector, "require");
                for (String selector : strings(injector, "method")) {
                    injections.add(new Injection(simple(injector.desc), mixin.name + "." + method.name,
                        method, selector, require < 0 ? 1 : require, list(injector, "at"),
                        captures.getOrDefault(method.name + method.desc, Map.of())));
                }
            }
        }
        assertFalse(injections.isEmpty(), "no injectors were found in " + mixin.name);
        return injections;
    }

    /**
     * The {@code @Local} parameters a mixin handler declares, keyed by parameter position. The capture
     * is written on the handler and resolved against the locals of the Minecraft method it targets, so
     * these live on the mixin class rather than on anything in the game.
     */
    private static Map<Integer, List<AnnotationNode>> captures(MethodNode method) {
        Map<Integer, List<AnnotationNode>> held = new LinkedHashMap<>();
        record(method.visibleParameterAnnotations, held);
        record(method.invisibleParameterAnnotations, held);
        return held;
    }

    private static void record(@Nullable List<AnnotationNode>[] perParameter,
                               Map<Integer, List<AnnotationNode>> held) {
        if (perParameter == null) {
            return;
        }
        for (int i = 0; i < perParameter.length; i++) {
            if (perParameter[i] != null) {
                held.put(i, perParameter[i]);
            }
        }
    }

    private static Map<String, MethodInfo> methods(String owner) {
        Collector collector = new Collector();
        new ClassReader(readMinecraft(owner)).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                             String[] exceptions) {
                return new Collector.Handler(collector, access, name, desc);
            }
        }, ClassReader.SKIP_FRAMES);
        return collector.methods;
    }

    private static final class Collector {
        private final Map<String, MethodInfo> methods = new LinkedHashMap<>();

        static final class Handler extends MethodVisitor {
            private final Collector owner;
            private final int access;
            private final String name;
            private final String desc;
            private final List<Local> locals = new ArrayList<>();
            private final Map<String, Integer> calls = new HashMap<>();

            Handler(Collector owner, int access, String name, String desc) {
                super(Opcodes.ASM9);
                this.owner = owner;
                this.access = access;
                this.name = name;
                this.desc = desc;
            }

            @Override
            public void visitLocalVariable(String localName, String localDesc, String signature,
                                           org.objectweb.asm.Label start, org.objectweb.asm.Label end,
                                           int index) {
                locals.add(new Local(localName, localDesc, index));
            }

            @Override
            public void visitMethodInsn(int opcode, String callOwner, String callName, String callDesc,
                                        boolean isInterface) {
                calls.merge(callOwner + "." + callName + callDesc, 1, Integer::sum);
            }

            @Override
            public void visitEnd() {
                owner.methods.put(name + desc, new MethodInfo(name, desc, access, locals, calls));
            }
        }
    }

    /**
     * Every mixin the config names, resolved against the package it declares. "mixins" and "client"
     * are two environments, not two packages, so both read from the same one.
     */
    private static List<String> listedMixins() {
        String text = new String(readMixinsJson(), StandardCharsets.UTF_8);
        String packageName = string(text, "package");
        assertFalse(packageName.isEmpty(), MIXIN_CONFIG + " declares no package");
        List<String> names = new ArrayList<>();
        for (String environment : List.of("mixins", "client", "server")) {
            for (String name : stringList(text, environment)) {
                names.add(packageName + "." + name);
            }
        }
        return names;
    }

    private static List<String> stringList(String json, String key) {
        List<String> names = new ArrayList<>();
        int at = json.indexOf('"' + key + '"');
        if (at < 0) {
            return names;
        }
        int open = json.indexOf('[', at);
        int close = json.indexOf(']', open);
        assertTrue(open > 0 && close > open, key + " is not a list in " + MIXIN_CONFIG);
        for (String part : json.substring(open + 1, close).split(",")) {
            String trimmed = part.trim();
            if (trimmed.length() > 2) {
                names.add(trimmed.substring(1, trimmed.length() - 1));
            }
        }
        return names;
    }

    private static String string(String json, String key) {
        String marker = '"' + key + '"';
        int at = json.indexOf(marker);
        if (at < 0) {
            return "";
        }
        int colon = json.indexOf(':', at);
        int open = json.indexOf('"', colon);
        int close = json.indexOf('"', open + 1);
        assertTrue(colon > 0 && open > colon && close > open, key + " is not a string in " + MIXIN_CONFIG);
        return json.substring(open + 1, close);
    }

    private static List<File> mixinClassFiles() {
        List<File> files = new ArrayList<>();
        for (File root : mainOutput()) {
            File[] candidates = new File(root, MIXIN_PATH)
                .listFiles(file -> file.getName().endsWith(".class") && !file.getName().contains("$"));
            if (candidates == null) {
                continue;
            }
            for (File candidate : candidates) {
                if (!files.contains(candidate)) {
                    files.add(candidate);
                }
            }
        }
        assertFalse(files.isEmpty(), "no class files under " + MIXIN_PATH + " in the main output");
        files.sort(File::compareTo);
        return files;
    }

    /**
     * The main output, told to the test by the build so that the classes the mod ships are the ones
     * checked. Falls back to the classpath, which is what an IDE running this directly hands over.
     */
    private static List<File> mainOutput() {
        String configured = System.getProperty("argon.mainClasses");
        if (configured != null && !configured.isEmpty()) {
            List<File> roots = new ArrayList<>();
            for (String entry : configured.split(File.pathSeparator)) {
                roots.add(new File(entry));
            }
            return roots;
        }
        List<File> roots = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File root = new File(entry);
            if (new File(root, MIXIN_PATH).isDirectory()) {
                roots.add(root);
            }
        }
        return roots;
    }

    /**
     * Reads a class straight out of whatever the game is built from, wherever that happens to sit on
     * the classpath. A missing target has to be reported as a missing target, not as a null read.
     */
    private static byte[] readMinecraft(String internalName) {
        String entry = internalName + ".class";
        for (String path : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File candidate = new File(path);
            if (candidate.isDirectory()) {
                File file = new File(candidate, entry);
                if (file.isFile()) {
                    return readFile(file);
                }
            } else if (candidate.isFile() && candidate.getName().endsWith(".jar")) {
                try (JarFile jar = new JarFile(candidate)) {
                    ZipEntry zip = jar.getEntry(entry);
                    if (zip != null) {
                        try (InputStream stream = jar.getInputStream(zip)) {
                            return stream.readAllBytes();
                        }
                    }
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }
        }
        throw new AssertionError(internalName + " is not on the test classpath, so it cannot be checked");
    }

    private static byte[] readMixinsJson() {
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            File file = new File(entry, MIXIN_CONFIG);
            if (file.isFile()) {
                return readFile(file);
            }
        }
        String resources = System.getProperty("argon.mainResources");
        if (resources != null && !resources.isEmpty()) {
            return readFile(new File(resources, MIXIN_CONFIG));
        }
        try (InputStream stream = MixinInjectionTest.class.getClassLoader().getResourceAsStream(MIXIN_CONFIG)) {
            assertNotNull(stream, MIXIN_CONFIG + " is not on the classpath");
            return stream.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static byte[] readFile(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Every annotation on a member, visible or not. Mixin and MixinExtras keep theirs at CLASS
     * retention, so a check that reads only the visible half finds no mixins at all.
     */
    private static List<AnnotationNode> allAnnotations(@Nullable List<AnnotationNode> visible,
                                                       @Nullable List<AnnotationNode> invisible) {
        List<AnnotationNode> all = new ArrayList<>();
        if (visible != null) {
            all.addAll(visible);
        }
        if (invisible != null) {
            all.addAll(invisible);
        }
        return all;
    }

    @Nullable
    private static AnnotationNode annotation(@Nullable List<AnnotationNode> annotations, String desc) {
        if (annotations == null) {
            return null;
        }
        for (AnnotationNode annotation : annotations) {
            if (Objects.equals(annotation.desc, desc)) {
                return annotation;
            }
        }
        return null;
    }

    private static int annotationInt(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        return value instanceof Integer number ? number : -1;
    }

    private static boolean annotationBoolean(AnnotationNode annotation, String key) {
        return Boolean.TRUE.equals(value(annotation, key));
    }

    @Nullable
    private static String string(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        return value instanceof String text ? text : null;
    }

    private static List<String> strings(AnnotationNode annotation, String key) {
        List<String> texts = new ArrayList<>();
        for (Object value : values(annotation, key)) {
            if (value instanceof String text) {
                texts.add(text);
            }
        }
        return texts;
    }

    @Nullable
    private static Object value(@Nullable AnnotationNode annotation, String key) {
        List<Object> pairs = annotation == null ? null : annotation.values;
        if (pairs == null) {
            return null;
        }
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            if (key.equals(pairs.get(i))) {
                return pairs.get(i + 1);
            }
        }
        return null;
    }

    private static List<Object> values(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        return value instanceof List<?> list ? new ArrayList<>(list) : List.of();
    }

    private static List<AnnotationNode> list(AnnotationNode annotation, String key) {
        List<AnnotationNode> nodes = new ArrayList<>();
        for (Object value : values(annotation, key)) {
            if (value instanceof AnnotationNode node) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    private static String simple(String descriptor) {
        String name = descriptor.substring(descriptor.lastIndexOf('/') + 1);
        return name.substring(0, name.indexOf(';'));
    }
}
