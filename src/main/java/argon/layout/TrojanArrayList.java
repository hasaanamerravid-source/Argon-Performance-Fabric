package argon.layout;

import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Records pool elements whose four rotations were already tested at this jigsaw. */
public final class TrojanArrayList<E> extends ArrayList<E> {
    public final Set<StructurePoolElement> elementsAlreadyParsed = new HashSet<>();
}
