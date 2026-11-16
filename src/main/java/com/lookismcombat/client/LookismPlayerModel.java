package com.lookismcombat.client;

import com.lookismcombat.ClientState;
import com.lookismcombat.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/** Player model with fighting-stance, punch, kick and sprint animations (only while a mode is active). */
public class LookismPlayerModel extends PlayerModel<AbstractClientPlayer> {

    public LookismPlayerModel(ModelPart root, boolean slim) { super(root, slim); }

    /** Arm/leg X-rotation over the swing (t = 0..1): wind-up -> strike -> recover */
    public static float curve(float t) {
        if (t < 0.25F) return Mth.lerp(t / 0.25F, 0F, 0.7F);
        if (t < 0.45F) return Mth.lerp((t - 0.25F) / 0.2F, 0.7F, -1.65F);
        return Mth.lerp((t - 0.45F) / 0.55F, -1.65F, 0F);
    }

    @Override
    public void setupAnim(AbstractClientPlayer p, float ls, float lsa, float age, float yaw, float pitch) {
        super.setupAnim(p, ls, lsa, age, yaw, pitch);

        ClientState.Info info = ClientState.INFO.get(p.getUUID());
        if (info == null || info.mode() == 0) return;
        if (p.isPassenger() || p.isFallFlying() || p.isSwimming() || p.isSleeping()) return;
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player && mc.options.getCameraType().isFirstPerson()) return;

        Mode m = Mode.byId(info.mode());
        boolean empty = p.getMainHandItem().isEmpty();
        boolean sprint = p.isSprinting() && lsa > 0.2F;
        boolean punching = this.attackTime > 0F && empty;
        boolean right = ClientState.rightFist(p);

        if (sprint) {
            // exaggerated running
            rightArm.xRot *= 1.7F; leftArm.xRot *= 1.7F;
            rightLeg.xRot *= 1.35F; leftLeg.xRot *= 1.35F;
        } else if (empty) {
            // fighting guard
            float br = Mth.sin(age * 0.12F) * 0.04F;
            rightArm.xRot = -1.15F + br; rightArm.yRot = -0.45F; rightArm.zRot = 0F;
            leftArm.xRot = -1.15F - br;  leftArm.yRot = 0.45F;  leftArm.zRot = 0F;
        }

        if (punching) {
            float c = curve(this.attackTime);
            if (m.kick) {
                ModelPart leg = right ? rightLeg : leftLeg;
                leg.xRot = c * 0.95F; leg.yRot = 0F; leg.zRot = 0F;
            } else {
                ModelPart arm = right ? rightArm : leftArm;
                arm.xRot = c; arm.yRot = right ? -0.15F : 0.15F; arm.zRot = 0F;
            }
        }

        hat.copyFrom(head); jacket.copyFrom(body);
        leftSleeve.copyFrom(leftArm); rightSleeve.copyFrom(rightArm);
        leftPants.copyFrom(leftLeg); rightPants.copyFrom(rightLeg);
    }
}
