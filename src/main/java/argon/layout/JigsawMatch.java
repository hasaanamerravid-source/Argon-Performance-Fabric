package argon.layout;

import net.minecraft.core.FrontAndTop;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import org.jspecify.annotations.Nullable;

/**
 * Same attach rules as JigsawBlock.canAttach, with one orientation lookup per block.
 *
 * <p>Vanilla reads the front and the top out of two properties per block. Both live in the single
 * {@code ORIENTATION} state, so one lookup per block yields both. The name is compared first, and a
 * null name on the child still matches anything, which is the guard vanilla has and the shorter
 * form has to keep.
 */
public final class JigsawMatch {
    private JigsawMatch() {
    }

    public static boolean canAttach(StructureTemplate.JigsawBlockInfo parent, StructureTemplate.JigsawBlockInfo child) {
        if (!nameMatches(parent.target(), child.name())) {
            return false;
        }
        return orientationsFit(parent, child);
    }

    private static boolean nameMatches(Identifier parentTarget, @Nullable Identifier childName) {
        return childName == null || parentTarget.equals(childName);
    }

    private static boolean orientationsFit(StructureTemplate.JigsawBlockInfo parent, StructureTemplate.JigsawBlockInfo child) {
        FrontAndTop parentOrientation = parent.state().getValue(JigsawBlock.ORIENTATION);
        FrontAndTop childOrientation = child.state().getValue(JigsawBlock.ORIENTATION);
        if (parentOrientation.front() != childOrientation.front().getOpposite()) {
            return false;
        }
        return parentOrientation.top() == childOrientation.top()
            || parent.jointType() == JigsawBlockEntity.JointType.ROLLABLE;
    }
}
