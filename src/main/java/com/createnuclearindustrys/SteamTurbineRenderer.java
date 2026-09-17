package com.createnuclearindustrys;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class SteamTurbineRenderer implements BlockEntityRenderer<SteamTurbineBlockEntity> {

    public SteamTurbineRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SteamTurbineBlockEntity be, float partialTick, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {

        Direction facing = be.getBlockState().getValue(DirectionalKineticBlock.FACING);
        BlockState shaftState = KineticBlockEntityRenderer.shaft(facing.getAxis());

        SuperByteBuffer buf = CachedBuffers.block(shaftState);
        KineticBlockEntityRenderer.standardKineticRotationTransform(buf, be, light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
