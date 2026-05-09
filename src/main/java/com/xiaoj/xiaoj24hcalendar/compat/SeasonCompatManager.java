package com.xiaoj.xiaoj24hcalendar.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import com.xiaoj.xiaoj24hcalendar.Xiaoj24HCalendar;
import com.xiaoj.xiaoj24hcalendar.calendar.CalendarDate;
import com.xiaoj.xiaoj24hcalendar.config.CalendarConfig;

/**
 * 统一管理外部季节模组的可选联动。
 *
 * <p>这里故意不直接引用 Ecliptic Seasons、Serene Seasons 或 Fabric Seasons 的类。这样玩家没有安装
 * 对应模组时，本模组仍然可以独立加载；只有检测到目标模组存在时，才进入各自的反射适配器。
 * 当 {@code calendar.enableSeasons} 关闭时，这里不会调用任何外部季节模组接口，
 * 让它们完全按自己的配置和时间规则运行。
 */
public final class SeasonCompatManager {

    /** Ecliptic Seasons 在 mods.toml 中声明的 mod id。 */
    private static final String ECLIPTIC_SEASONS_MOD_ID = "eclipticseasons";

    /** Serene Seasons 在 mods.toml 中声明的 mod id。 */
    private static final String SERENE_SEASONS_MOD_ID = "sereneseasons";

    /** Fabric Seasons / Forge Seasons 在 mod 元数据中声明的 mod id。 */
    private static final String FABRIC_SEASONS_MOD_ID = "seasons";

    /** 尚未同步过任何日期时使用的哨兵值。 */
    private static final int UNSYNCED_DATE = Integer.MIN_VALUE;

    /** 当前 JVM 中最近同步过的服务器实例，用于区分单人游戏反复开关世界的情况。 */
    private static MinecraftServer lastSyncedServer;

    /** 最近同步到外部季节模组的本模组循环日序号。 */
    private static int lastSyncedCycleIndex = UNSYNCED_DATE;

    /** 避免同一个 Ecliptic Seasons 反射错误在日志里刷屏。 */
    private static boolean reportedEclipticFailure;

    /** 避免同一个 Serene Seasons 反射错误在日志里刷屏。 */
    private static boolean reportedSereneFailure;

    /** 避免同一个 Fabric Seasons / Forge Seasons 反射错误在日志里刷屏。 */
    private static boolean reportedFabricFailure;

    /** 避免多个季节模组冲突时每 tick 重复记录日志。 */
    private static boolean reportedSeasonConflict;

    private SeasonCompatManager() {
    }

    /**
     * 当日期发生变化或首次进入世界时，同步外部季节模组。
     *
     * @param overworld 当前服务器主世界
     * @param date 本模组当前日期
     * @return 如果同步过程调整了原版世界时间，返回 {@code true}
     */
    public static boolean syncIfNeeded(ServerLevel overworld, CalendarDate date) {
        if (!CalendarConfig.enableMonths() || !CalendarConfig.enableSeasons()) {
            lastSyncedCycleIndex = UNSYNCED_DATE;
            return false;
        }

        resetCacheForNewServer(overworld.getServer());

        int cycleIndex = date.toCycleIndex();
        if (cycleIndex == lastSyncedCycleIndex) {
            return false;
        }

        return forceSync(overworld, date);
    }

    /**
     * 强制把当前日期写入外部季节模组。
     *
     * <p>命令手动设置日期时需要立即同步，即使日期序号和上一次相同，也可能是玩家想修正
     * 外部模组状态，因此这里不会使用 {@link #lastSyncedCycleIndex} 做提前返回。
     *
     * @param overworld 当前服务器主世界
     * @param date 本模组当前日期
     * @return 如果同步过程调整了原版世界时间，返回 {@code true}
     */
    public static boolean forceSync(ServerLevel overworld, CalendarDate date) {
        if (!CalendarConfig.enableMonths() || !CalendarConfig.enableSeasons()) {
            lastSyncedCycleIndex = UNSYNCED_DATE;
            return false;
        }

        if (!overworld.dimension().equals(Level.OVERWORLD)) {
            return false;
        }

        resetCacheForNewServer(overworld.getServer());

        int loadedSeasonMods = loadedSeasonModCount();
        if (loadedSeasonMods != 1) {
            lastSyncedCycleIndex = UNSYNCED_DATE;
            reportConflictIfNeeded(loadedSeasonMods);
            return false;
        }

        boolean changedWorldTime = false;
        if (isEclipticSeasonsLoaded()) {
            syncEclipticSeasons(overworld, date);
        } else if (isSereneSeasonsLoaded()) {
            syncSereneSeasons(overworld, date);
        } else if (isFabricSeasonsLoaded()) {
            changedWorldTime = syncFabricSeasons(overworld, date);
        }

        lastSyncedCycleIndex = date.toCycleIndex();
        return changedWorldTime;
    }

    /**
     * 检查指定模组是否已加载。
     *
     * @param modId 目标模组 id
     * @return 如果目标模组已加载，返回 {@code true}
     */
    private static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /**
     * 检查 Ecliptic Seasons 是否已加载。
     *
     * @return 如果当前整合包安装了 Ecliptic Seasons，返回 {@code true}
     */
    public static boolean isEclipticSeasonsLoaded() {
        return isLoaded(ECLIPTIC_SEASONS_MOD_ID);
    }

    /**
     * 检查 Serene Seasons 是否已加载。
     *
     * @return 如果当前整合包安装了 Serene Seasons，返回 {@code true}
     */
    public static boolean isSereneSeasonsLoaded() {
        return isLoaded(SERENE_SEASONS_MOD_ID);
    }

    /**
     * 检查 Fabric Seasons / Forge Seasons 是否已加载。
     *
     * @return 如果当前整合包安装了 Fabric Seasons / Forge Seasons，返回 {@code true}
     */
    public static boolean isFabricSeasonsLoaded() {
        return isLoaded(FABRIC_SEASONS_MOD_ID);
    }

    /**
     * 统计当前已加载的受支持季节模组数量。
     *
     * <p>这些模组都会尝试控制同一套“当前季节”概念。为了避免两个模组互相覆盖或让玩家误以为
     * 本模组在同时驱动多个季节系统，只要数量不是 1，联动就不会生效。
     *
     * @return 已加载的受支持季节模组数量
     */
    public static int loadedSeasonModCount() {
        int count = 0;
        if (isEclipticSeasonsLoaded()) {
            count++;
        }
        if (isSereneSeasonsLoaded()) {
            count++;
        }
        if (isFabricSeasonsLoaded()) {
            count++;
        }

        return count;
    }

    /**
     * 判断当前是否存在多个受支持季节模组冲突。
     *
     * @return 如果加载了两个或更多受支持季节模组，返回 {@code true}
     */
    public static boolean hasSeasonCompatConflict() {
        return loadedSeasonModCount() > 1;
    }

    /**
     * 在服务器实例变化时清空内存同步游标。
     *
     * <p>单人游戏从一个世界退出再进入另一个世界时，JVM 可能没有重启。若不重置游标，
     * 新世界刚好处在同一天时就会跳过首次同步。
     *
     * @param server 当前服务器实例
     */
    private static void resetCacheForNewServer(MinecraftServer server) {
        if (lastSyncedServer == server) {
            return;
        }

        lastSyncedServer = server;
        lastSyncedCycleIndex = UNSYNCED_DATE;
        reportedEclipticFailure = false;
        reportedSereneFailure = false;
        reportedFabricFailure = false;
        reportedSeasonConflict = false;
    }

    /**
     * 在季节模组数量不满足联动条件时记录一次日志。
     *
     * @param loadedSeasonMods 当前已加载的受支持季节模组数量
     */
    private static void reportConflictIfNeeded(int loadedSeasonMods) {
        if (loadedSeasonMods <= 1 || reportedSeasonConflict) {
            return;
        }

        Xiaoj24HCalendar.LOGGER.warn("检测到多个受支持的季节模组，Xiaoj's 24H Calendar 的季节联动已失效。");
        reportedSeasonConflict = true;
    }

    /**
     * 调用 Ecliptic Seasons 适配器，并把失败压缩成一次日志。
     *
     * @param overworld 当前服务器主世界
     * @param date 本模组当前日期
     */
    private static void syncEclipticSeasons(ServerLevel overworld, CalendarDate date) {
        try {
            EclipticSeasonsCompat.sync(overworld, date);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (!reportedEclipticFailure) {
                Xiaoj24HCalendar.LOGGER.warn("同步 Ecliptic Seasons 季节状态失败，后续将继续运行但不再重复刷屏。", error);
                reportedEclipticFailure = true;
            }
        }
    }

    /**
     * 调用 Serene Seasons 适配器，并把失败压缩成一次日志。
     *
     * @param overworld 当前服务器主世界
     * @param date 本模组当前日期
     */
    private static void syncSereneSeasons(ServerLevel overworld, CalendarDate date) {
        try {
            SereneSeasonsCompat.sync(overworld, date);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (!reportedSereneFailure) {
                Xiaoj24HCalendar.LOGGER.warn("同步 Serene Seasons 季节状态失败，后续将继续运行但不再重复刷屏。", error);
                reportedSereneFailure = true;
            }
        }
    }

    /**
     * 调用 Fabric Seasons / Forge Seasons 适配器，并把失败压缩成一次日志。
     *
     * @param overworld 当前服务器主世界
     * @param date 本模组当前日期
     * @return 如果本次同步调整了原版世界时间，返回 {@code true}
     */
    private static boolean syncFabricSeasons(ServerLevel overworld, CalendarDate date) {
        try {
            return FabricSeasonsCompat.sync(overworld, date);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (!reportedFabricFailure) {
                Xiaoj24HCalendar.LOGGER.warn("同步 Fabric Seasons / Forge Seasons 季节状态失败，后续将继续运行但不再重复刷屏。", error);
                reportedFabricFailure = true;
            }
        }

        return false;
    }
}
