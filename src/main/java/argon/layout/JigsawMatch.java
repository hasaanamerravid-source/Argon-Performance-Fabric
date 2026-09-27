package argon.layout;

import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Same attach rules as JigsawBlock.canAttach, with one orientation lookup per block. */
public final class JigsawMatch {
    private JigsawMatch() {
    }

    public static boolean canAttach(StructureTemplate.JigsawBlockInfo parent, StructureTemplate.JigsawBlockInfo child) {
        FrontAndTop parentOrientation = parent.state().getValue(JigsawBlock.ORIENTATION);
        FrontAndTop childOrientation = child.state().getValue(JigsawBlock.ORIENTATION);
        boolean rollable = parent.jointType() == JigsawBlockEntity.JointType.ROLLABLE;
        return parentOrientation.front() == childOrientation.front().getOpposite()
                && (parentOrientation.top() == childOrientation.top() || rollable)
                && parent.target().equals(child.name());
    }
}
