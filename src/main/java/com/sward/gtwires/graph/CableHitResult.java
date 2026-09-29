package com.sward.gtwires.graph;

import net.minecraft.world.phys.Vec3;

public record CableHitResult(Cable cable, Vec3 hit, double distance)
{
}
