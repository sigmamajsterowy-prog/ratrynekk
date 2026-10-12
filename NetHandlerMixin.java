package pl.ratrynek.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.ratrynek.AutoBuyer;

@Mixin(ClientPlayNetworkHandler.class)
public class NetHandlerMixin {
    @Inject(method = "onInventory", at = @At("TAIL"))
    private void ratrynek$onInventory(InventoryS2CPacket packet, CallbackInfo ci) {
        AutoBuyer.scan(MinecraftClient.getInstance());
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"))
    private void ratrynek$onSlot(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        AutoBuyer.scan(MinecraftClient.getInstance());
    }
}
