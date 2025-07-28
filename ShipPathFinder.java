package com.lulan.shincolle.ai.path;

import com.lulan.shincolle.block.BlockWaypoint;
import com.lulan.shincolle.reference.Enums;
import com.lulan.shincolle.utility.BlockHelper;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.IntHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public class ShipPathFinder {
    private final IBlockAccess world;
    private final ShipPathHeap path = new ShipPathHeap();
    private final IntHashMap<ShipPathPoint> pointMap = new IntHashMap<>();
    private final boolean canEntityFly;

    public ShipPathFinder(IBlockAccess world, boolean canFly) {
        this.world = world;
        this.canEntityFly = canFly;
    }

    @Nullable
    public ShipPath findPath(Entity fromEnt, Entity toEnt, float range) {
        return this.findPath(fromEnt, toEnt.posX, toEnt.posY, toEnt.posZ, range);
    }

    @Nullable
    public ShipPath findPath(Entity entity, int x, int y, int z, float range) {
        return this.findPath(entity, x + 0.5f, y + 0.5f, z + 0.5f, range);
    }
    @Nullable
    private ShipPath findPath(Entity entity, double tx, double ty, double tz, float range) {
        path.clearPath();
        pointMap.clearMap();
        int sx = MathHelper.floor(entity.posX + 0.5);
        int sy = MathHelper.floor(entity.posY + 0.5);
        int sz = MathHelper.floor(entity.posZ + 0.5);
        ShipPathPoint changablestart;
        final ShipPathPoint finalstart = openPoint(sx, sy, sz);
        int ex = MathHelper.floor(tx);
        int ey = MathHelper.floor(ty);
        int ez = MathHelper.floor(tz);
        ShipPathPoint changableend;
        final ShipPathPoint finalend = openPoint(ex, ey, ez);
        ShipPathPoint size = new ShipPathPoint(
                MathHelper.floor(entity.width + 1.0f),
                MathHelper.floor(entity.height + 1.0f),
                MathHelper.floor(entity.width + 1.0f)
        );
        ShipPath targetpath1 = computePath(entity, finalstart, finalend, size, range);
        if(targetpath1 == null) return null;
        if(ex != targetpath1.getFinalPathPoint().xCoord || ey != targetpath1.getFinalPathPoint().yCoord || ez != targetpath1.getFinalPathPoint().zCoord) {
            if(ty - entity.posY > 2 || ty - entity.posY < -2) {
                BlockPos ladder = FindLadder(sx, sy, sz, 10, ty - entity.posY > 2);
                if(ladder != null){
                    changableend = openPoint(ladder.getX(), ladder.getY(), ladder.getZ());
                    pointMap.clearMap();
                    targetpath1 = computePath(entity, finalstart, changableend, size, range);
                    changablestart = openPoint(ladder.getX(), ey, ladder.getZ());
                    pointMap.clearMap();
                    ShipPath targetpath2 = computePath(entity, changablestart, finalend, size, range);
                    if(targetpath1 == null || targetpath2 == null) return null;
                    ShipPathPoint[] points1 = targetpath1.getPathPoints();
                    ShipPathPoint[] points2 = targetpath2.getPathPoints();
                    List<ShipPathPoint> combinedList = new ArrayList<>(Arrays.asList(points1));
                    combinedList.addAll(Arrays.asList(points2));
                    return new ShipPath(combinedList.toArray(new ShipPathPoint[0]));
                }
            }
        }
        return targetpath1;
    }

    @Nullable
    private ShipPath computePath(Entity entity, ShipPathPoint start, ShipPathPoint end, ShipPathPoint size, float range) {
        start.initForSearch(end);
        path.clearPath();
        path.addPoint(start);
        ShipPathPoint best = start;
        int iterations = 0;
        while (!path.isPathEmpty()) {
            ShipPathPoint current = path.dequeue();
            iterations++;
            if (current.equals(end)) {
                return createEntityPath(end);
            }
            if (iterations > 1000) break;
            if (current.distanceToSquared(end) < best.distanceToSquared(end)) {
                best = current;
            }
            current.setVisited(true);
            for (ShipPathPoint neighbor : findPathOptions(entity, current, size, end, range)) {
                if (!neighbor.initPathParameters(current, end, range)) continue;
                if (neighbor.isAssigned()) {
                    path.changeDistance(neighbor, neighbor.getTotalPathDistance() + neighbor.getDistanceToNext());
                } else {
                    neighbor.setDistanceToTarget(neighbor.getTotalPathDistance() + neighbor.getDistanceToNext());
                    path.addPoint(neighbor);
                }
            }
        }
        if (best == start) return null;
        return createEntityPath(best);
    }

    private ShipPathPoint[] findPathOptions(Entity entity, ShipPathPoint current, ShipPathPoint size, ShipPathPoint target, float range) {
        Enums.EnumPathType type = getPathType(current.xCoord, current.yCoord + 1, current.zCoord, size);
        int yOffset = (type == Enums.EnumPathType.FLUID || type == Enums.EnumPathType.OPEN) ? MathHelper.floor(Math.max(1.0f, entity.stepHeight)) : 0;
        ArrayList<ShipPathPoint> list = new ArrayList<>();
        int[][] dirs = new int[][]{{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}};
        for (int[] d : dirs) {
            ShipPathPoint np = getSafePoint(current.xCoord + d[0], current.yCoord + d[1], current.zCoord + d[2], size, yOffset);
            if (np != null && !np.isVisited() && np.distanceTo(target) < range) {
                list.add(np);
            }
        }
        return list.toArray(new ShipPathPoint[0]);
    }

    private ShipPathPoint getSafePoint(int x, int y, int z, ShipPathPoint size, int yOffset) {
        Enums.EnumPathType pt = getPathType(x, y, z, size);
        if (pt == Enums.EnumPathType.LADDER || pt == Enums.EnumPathType.FLUID || pt == Enums.EnumPathType.OPENABLE) {
            return openPoint(x, y, z);
        }
        ShipPathPoint p = (pt == Enums.EnumPathType.OPEN)? openPoint(x,y,z) : null;
        if (p == null && yOffset > 0 && pt != Enums.EnumPathType.FENCE) {
            return getSafePoint(x, y+1, z, size, yOffset-1);
        }
        if (p != null && !canEntityFly) {
            int steps = 0;
            while (y > 0 && steps++ < 64) {
                Enums.EnumPathType below = getPathType(x, y-1, z, size);
                if (below == Enums.EnumPathType.FLUID) {
                    return openPoint(x, --y, z);
                }
                if (below != Enums.EnumPathType.OPEN) break;
                p = openPoint(x, --y, z);
            }
            if (steps > 64) return null;
        }
        return p;
    }

    private ShipPathPoint openPoint(int x, int y, int z) {
        int h = ShipPathPoint.makeHash(x, y, z);
        ShipPathPoint pt = pointMap.lookup(h);
        if (pt == null) {
            pt = new ShipPathPoint(x, y, z);
            pointMap.addKey(h, pt);
        }
        return pt;
    }

    public Enums.EnumPathType getPathType(int x, int y, int z, ShipPathPoint size) {
        EnumSet<Enums.EnumPathType> types = EnumSet.noneOf(Enums.EnumPathType.class);
        int doors = 0;
        for (int ix = x; ix < x + size.xCoord; ix++) {
            for (int iy = y + size.yCoord - 1; iy >= y; iy--) {
                for (int iz = z; iz < z + size.zCoord; iz++) {
                    BlockPos pos = new BlockPos(ix, iy, iz);
                    IBlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();
                    Material m = state.getMaterial();
                    if (block instanceof BlockLadder) {
                        types.add(Enums.EnumPathType.LADDER);
                        continue;
                    }
                    if (m == Material.AIR) continue;
                    if (block instanceof BlockFence || block instanceof BlockWall) {
                        return iy == y? Enums.EnumPathType.FENCE : Enums.EnumPathType.BLOCKED;
                    }
                    if (block instanceof BlockDoor) {
                        if (m == Material.IRON || ++doors > 4) {
                            return Enums.EnumPathType.BLOCKED;
                        }
                        types.add(Enums.EnumPathType.OPENABLE);
                    } else if (block instanceof BlockFenceGate) {
                        types.add(Enums.EnumPathType.OPENABLE);
                    } else if (BlockHelper.checkBlockIsLiquid(state)) {
                        types.add(Enums.EnumPathType.FLUID);
                    } else if (!(block instanceof BlockRailBase)
                            && !(block instanceof BlockWaypoint)
                            && (block instanceof BlockLilyPad || !block.isPassable(world, pos))) {
                        return Enums.EnumPathType.BLOCKED;
                    } else {
                        types.add(Enums.EnumPathType.OPEN);
                    }
                }
            }
        }
        if (types.contains(Enums.EnumPathType.LADDER)) return Enums.EnumPathType.LADDER;
        if (types.contains(Enums.EnumPathType.OPENABLE)) return Enums.EnumPathType.OPENABLE;
        if (types.contains(Enums.EnumPathType.FLUID)) return Enums.EnumPathType.FLUID;
        return Enums.EnumPathType.OPEN;
    }

    private BlockPos FindLadder(int sx, int sy, int sz, int radius, Boolean up){
        BlockPos origin = new BlockPos(sx, sy, sz);
        for (int dist = 0; dist <= radius; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dz = -dist; dz <= dist; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != dist) continue;
                    BlockPos candidate = origin.add(dx, 0, dz);
                    if ((up && checkVerticalLadder(candidate, 0, +2)) || (!up && checkVerticalLadder(candidate, -1, -2))) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean checkVerticalLadder(BlockPos basePos, int startOffsetY, int endOffsetY) {
        int step = (endOffsetY >= startOffsetY) ? +1 : -1;
        for (int dy = startOffsetY; dy != endOffsetY + step; dy += step) {
            BlockPos p = basePos.up(dy);
            Block b = this.world.getBlockState(p).getBlock();
            if (b != Blocks.LADDER) {
                return false;
            }
        }
        return true;
    }

    private ShipPath createEntityPath(ShipPathPoint end) {
        ArrayList<ShipPathPoint> pathList = new ArrayList<>();
        ShipPathPoint cur = end;
        while (cur != null) {
            pathList.add(cur);
            cur = cur.getPrevious();
        }
        java.util.Collections.reverse(pathList);
        return new ShipPath(pathList.toArray(new ShipPathPoint[0]));
    }
}
