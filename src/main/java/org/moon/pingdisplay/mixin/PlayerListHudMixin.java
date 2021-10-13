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
        return r + pingWidth;
    }

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/PlayerListHud;renderScoreboardObjective(Lnet/minecraft/scoreboard/ScoreboardObjective;ILjava/lang/String;IILnet/minecraft/client/network/PlayerListEntry;Lnet/minecraft/client/util/math/MatrixStack;)V"))
    private void renderScoreboard(Args args) {
        ScoreboardObjective objective = args.get(0);

        int startX = args.get(3);
        int endX = args.get(4);

        if (objective.getRenderType() != ScoreboardCriterion.RenderType.HEARTS)
            args.set(3, startX - pingWidth);

        args.set(4, endX - pingWidth);
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
        else if (latency < 0)
            color = 0x5B5B5B;
        else if (latency < 100)
            color = 0x00FF21;
        else if (latency < 250)
            color = 0x9EFF00;
        else if (latency < 500)
            color = 0xDEFF00;
        else if (latency < 1000)
            color = 0xFFA100;
        else if (latency > 9999) {
            latency = 9999;
            color = 0xFF72B7;
            plus = true;
        }
        else
            color = 0xFF2100;

        drawTextWithShadow(matrices, this.client.textRenderer, new LiteralText(inf ? ":3" : latency + (plus ? "+" : "") + "ms"), x + width - pingWidth, y, color);
        ci.cancel();
    }
}
