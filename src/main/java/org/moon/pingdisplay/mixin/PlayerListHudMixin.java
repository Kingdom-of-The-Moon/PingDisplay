package org.moon.pingdisplay.mixin;

import com.google.common.collect.Ordering;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.awt.Color;
import java.util.List;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin extends DrawableHelper {

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private static Ordering<PlayerListEntry> ENTRY_ORDERING;

    @Unique int pingWidth;

    @Inject(method = "render", at = @At("HEAD"))
    private void render(MatrixStack matrices, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        if (this.client.player == null) return;

        ClientPlayNetworkHandler clientPlayNetworkHandler = this.client.player.networkHandler;
        List<PlayerListEntry> list = ENTRY_ORDERING.sortedCopy(clientPlayNetworkHandler.getPlayerList());

        Integer latency = null;
        for (PlayerListEntry playerListEntry : list) {
            int ping = playerListEntry.getLatency();
            if (latency == null || ping > latency) latency = ping;
        }

        boolean nope = false;
        boolean plus = false;
        boolean inf = false;

        if (latency == null)
            nope = true;
        else if (latency > 9999) {
            latency = 9999;
            plus = true;
        }
        else if (latency < -9999) {
            inf = true;
        }

        pingWidth = this.client.textRenderer.getWidth(new LiteralText(nope ? "" : inf ? ":3" : latency + (plus ? "+" : "") + "ms"));
    }

    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 6)
    private int changeListWidth(int r) {
        return r + pingWidth - (this.client.isInSingleplayer() || this.client.getNetworkHandler().getConnection().isEncrypted() ? 9 : 0);
    }

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/PlayerListHud;renderScoreboardObjective(Lnet/minecraft/scoreboard/ScoreboardObjective;ILjava/lang/String;IILnet/minecraft/client/network/PlayerListEntry;Lnet/minecraft/client/util/math/MatrixStack;)V"))
    private void renderScoreboard(Args args) {
        ScoreboardObjective objective = args.get(0);

        int startX = args.get(3);
        int endX = args.get(4);

        int offset = (this.client.isInSingleplayer() || this.client.getNetworkHandler().getConnection().isEncrypted() ? 9 : 0);

        args.set(3, startX + (objective.getRenderType() != ScoreboardCriterion.RenderType.HEARTS ? -pingWidth + offset : 2));
        args.set(4, endX - pingWidth + offset);
    }

    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
    private void renderLatencyIcon(MatrixStack matrices, int width, int x, int y, PlayerListEntry entry, CallbackInfo ci) {
        int color;
        boolean plus = false;
        boolean inf = false;

        int latency = entry.getLatency();
        if (latency < -9999) {
            color = 0xFF72B7;
            inf = true;
        }
        else if (latency < 0) // < 0
            color = 0x5B5B5B;
        else if (latency < 150) // 0 - 150
            color = lerpColor(0x00FF21, 0x9EFF00, getPercentage(0, 150, latency));
        else if (latency < 300) // 150 - 300
            color = lerpColor(0x9EFF00, 0xDEFF00, getPercentage(150, 300, latency));
        else if (latency < 650) // 300 - 650
            color = lerpColor(0xDEFF00, 0xFFA100, getPercentage(300, 650, latency));
        else if (latency < 1000) // 650 - 1000
            color = lerpColor(0xFFA100, 0xFF2100, getPercentage(650, 1000, latency));
        else if (latency > 9999) { // > 9999
            latency = 9999;
            color = 0xFF72B7;
            plus = true;
        }
        else // 1000 - 9999
            color = 0xFF2100;

        drawTextWithShadow(matrices, this.client.textRenderer, new LiteralText(inf ? ":3" : latency + (plus ? "+" : "") + "ms"), x + width - pingWidth, y, color);
        ci.cancel();
    }

    @Unique
    private static int lerpColor(int colorA, int colorB, float lerp) {
        Color x = new Color(colorA);
        Color y = new Color(colorB);

        float r = MathHelper.lerp(lerp, x.getRed(), y.getRed());
        float g = MathHelper.lerp(lerp, x.getGreen(), y.getGreen());
        float b = MathHelper.lerp(lerp, x.getBlue(), y.getBlue());

        return new Color(r / 255f, g / 255f, b / 255f).getRGB();
    }

    @Unique
    private static float getPercentage(float min, float max, float input) {
        return (input - min) / (max - min);
    }
}
