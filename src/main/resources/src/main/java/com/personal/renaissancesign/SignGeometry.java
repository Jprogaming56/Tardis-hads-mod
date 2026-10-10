package com.personal.renaissancesign;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Builds the replacement sign band for the police box model.
 *
 * Same place and footprint as AiT's own PCB_t part (32 wide, 4 deep, hung from the roof with the part pivot at
 * y = -1), but one unit taller, with a dark inset panel and the lettering made of small lit blocks. Every cube
 * samples a flat colour patch in the otherwise unused corner of the texture (see the PATCH constants), so it works
 * with the 512x512 texture layout AiT already uses and needs no UV work.
 */
final class SignGeometry {
    private SignGeometry() {}

    // Flat colour patches added to the textures by this mod (x, y of the top-left corner).
    private static final int FRAME_U = 300, FRAME_V = 300; // 80x12, body teal
    private static final int DARK_U = 300, DARK_V = 320;   // 80x12, near black
    private static final int LIGHT_U = 300, LIGHT_V = 340; // 16x16, warm cream, also in the emission texture

    // Local to the part pivot (0, -1, 0): AiT's own sign spans y -64..-59 here; the new one runs -64..-58.
    private static final float FRAME_TOP = -64f;
    private static final float FRAME_H = 6f;
    private static final float FRAME_Z = -19f;   // outer face
    private static final float FRAME_D = 4f;

    private static final float PANEL_TOP = -63.5f;
    private static final float PANEL_Z = -19.2f; // 0.2 proud of the frame
    private static final float PANEL_D = 0.25f;

    private static final float LETTER_Z = -19.45f; // 0.25 proud of the panel, overlapping it slightly
    private static final float LETTER_D = 0.30f;

    /** One sign face, facing -z (north) before rotation. */
    private static CubeListBuilder face() {
        CubeListBuilder b = CubeListBuilder.create();

        b.texOffs(FRAME_U, FRAME_V).addBox(-16f, FRAME_TOP, FRAME_Z, 32f, FRAME_H, FRAME_D);

        float half = (float) (SignLayout.PANEL_W / 2.0);
        b.texOffs(DARK_U, DARK_V).addBox(-half, PANEL_TOP, PANEL_Z, (float) SignLayout.PANEL_W,
                (float) SignLayout.PANEL_H, PANEL_D);

        for (SignLayout.Rect r : SignLayout.rects()) {
            // Seen from outside on the -z face, +x points to the reader's left, so mirror the x range.
            float x0 = (float) (half - r.x1());
            float x1 = (float) (half - r.x0());
            float y0 = PANEL_TOP + (float) r.y0();
            float y1 = PANEL_TOP + (float) r.y1();
            b.texOffs(LIGHT_U, LIGHT_V).addBox(x0, y0, LETTER_Z, x1 - x0, y1 - y0, LETTER_D);
        }
        return b;
    }

    /** A fresh PCB_t-shaped part: this face plus the same face turned 90, 180 and 270 degrees. */
    static ModelPart bake() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition pcb = root.addOrReplaceChild("PCB_t", face(), PartPose.offset(0f, -1f, 0f));
        pcb.addOrReplaceChild("sign_east", face(), PartPose.rotation(0f, 1.5708f, 0f));
        pcb.addOrReplaceChild("sign_south", face(), PartPose.rotation(0f, 3.1416f, 0f));
        pcb.addOrReplaceChild("sign_west", face(), PartPose.rotation(0f, -1.5708f, 0f));
        return LayerDefinition.create(mesh, 512, 512).bakeRoot().getChild("PCB_t");
    }
}
