package com.xiaoj.xiaoj24hcalendar.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import com.xiaoj.xiaoj24hcalendar.calendar.CalendarDate;

/**
 * Fabric Seasons / Forge Seasons 的反射兼容层。
 *
 * <p>这两个模组的 mod id 都是 {@code seasons}，并且只有春、夏、秋、冬四个大季节。
 * 它们不保存类似 Serene Seasons 的 {@code seasonCycleTicks}，而是直接根据世界
 * {@code dayTime} 与配置中的每季长度计算当前季节。因此这里会把本模组日期换算成
 * 对方一年循环中的 tick，并同步调整所有维度的世界时间。
 */
final class FabricSeasonsCompat {

    /** Forge Seasons 1.21.1 NeoForge 移植版的主类。 */
    private static final String FORGE_SEASONS_CLASS = "one.armelin.seasons.ForgeSeasons";

    /** Fabric Seasons 原版主类；通过兼容层运行 Fabric mod 时可能会使用这个包名。 */
    private static final String FABRIC_SEASONS_CLASS = "io.github.lucaargolo.seasons.FabricSeasons";

    /** Fabric Seasons 的 Season 枚举顺序：SPRING、SUMMER、FALL、WINTER。 */
    private static final int SEASON_COUNT = 4;

    /** 反射缓存：主类上的 CONFIG 字段。 */
    private static Field configField;

    /** 反射缓存：ModConfig.getSpringLength()。 */
    private static Method getSpringLengthMethod;

    /** 反射缓存：ModConfig.getSummerLength()。 */
    private static Method getSummerLengthMethod;

    /** 反射缓存：ModConfig.getFallLength()。 */
    private static Method getFallLengthMethod;

    /** 反射缓存：ModConfig.getWinterLength()。 */
    private static Method getWinterLengthMethod;

    /** 反射缓存：ModConfig.getStartingSeason()。 */
    private static Method getStartingSeasonMethod;

    /** 反射缓存：ModConfig.isSeasonLocked()。 */
    private static Method isSeasonLockedMethod;

    /** 反射缓存：ModConfig.isSeasonTiedWithSystemTime()。 */
    private static Method isSeasonTiedWithSystemTimeMethod;

    /** 反射缓存：ModConfig.isValidInDimension(ResourceKey&lt;Level&gt;)。 */
    private static Method isValidInDimensionMethod;

    private FabricSeasonsCompat() {
    }

    /**
     * 把本模组日期同步到 Fabric Seasons / Forge Seasons。
     *
     * <p>返回值用于告诉上层是否需要重绑本模组的跨天游标。只有当世界时间真的被改动时，
     * 才返回 {@code true}。
     *
     * @param overworld 当前服务器主世界
     * @param date 本模组当前日期
     * @return 如果本次同步调整了世界时间，返回 {@code true}
     * @throws ReflectiveOperationException 当目标模组 API/内部结构变化导致反射失败时抛出
     */
    static boolean sync(ServerLevel overworld, CalendarDate date) throws ReflectiveOperationException {
        ensureInitialized();

        Object config = configField.get(null);
        if (config == null || isUncontrollable(config) || !isValidInDimension(config, overworld)) {
            return false;
        }

        long[] seasonLengths = seasonLengths(config);
        if (!hasValidSeasonLengths(seasonLengths)) {
            return false;
        }

        int startingSeasonIndex = startingSeasonIndex(config);
        long yearLength = yearLength(seasonLengths);
        long targetCycleTicks = toFabricSeasonCycleTicks(date, overworld, seasonLengths, startingSeasonIndex);
        long targetDayTime = alignToCurrentSeasonYear(overworld.getDayTime(), yearLength, targetCycleTicks);

        return setAllLevelDayTimes(overworld, targetDayTime);
    }

    /**
     * 懒加载 Fabric Seasons / Forge Seasons 的反射入口。
     *
     * <p>NeoForge 环境通常加载的是 {@code one.armelin.seasons.ForgeSeasons}；
     * 如果玩家通过兼容层加载原 Fabric 版，则再尝试 Fabric 原包名。这里不直接引用这些类，
     * 避免玩家未安装目标模组时本模组启动失败。
     *
     * @throws ReflectiveOperationException 当类、字段或方法不存在时抛出
     */
    private static void ensureInitialized() throws ReflectiveOperationException {
        if (configField != null) {
            return;
        }

        Class<?> seasonsClass = findSeasonsClass();
        configField = seasonsClass.getField("CONFIG");

        Class<?> configClass = configField.getType();
        getSpringLengthMethod = configClass.getMethod("getSpringLength");
        getSummerLengthMethod = configClass.getMethod("getSummerLength");
        getFallLengthMethod = configClass.getMethod("getFallLength");
        getWinterLengthMethod = configClass.getMethod("getWinterLength");
        getStartingSeasonMethod = configClass.getMethod("getStartingSeason");
        isSeasonLockedMethod = configClass.getMethod("isSeasonLocked");
        isSeasonTiedWithSystemTimeMethod = configClass.getMethod("isSeasonTiedWithSystemTime");
        isValidInDimensionMethod = findSingleArgumentMethod(configClass, "isValidInDimension");
    }

    /**
     * 查找当前运行环境中实际存在的 Seasons 主类。
     *
     * @return 已加载的 Fabric Seasons 或 Forge Seasons 主类
     * @throws ClassNotFoundException 当两个候选类都不存在时抛出
     */
    private static Class<?> findSeasonsClass() throws ClassNotFoundException {
        try {
            return Class.forName(FORGE_SEASONS_CLASS);
        } catch (ClassNotFoundException forgeMissing) {
            try {
                return Class.forName(FABRIC_SEASONS_CLASS);
            } catch (ClassNotFoundException fabricMissing) {
                fabricMissing.addSuppressed(forgeMissing);
                throw fabricMissing;
            }
        }
    }

    /**
     * 查找只有一个参数的指定方法。
     *
     * <p>Fabric 原版 jar 和 NeoForge 移植版的 Minecraft 参数类型可能不同。这里只按名称和
     * 参数数量缓存，调用时再把当前世界的维度 key 传进去，从而兼容官方命名和运行期重映射。
     *
     * @param configClass 目标配置类
     * @param name 方法名称
     * @return 找到的方法；没有找到时返回 {@code null}
     */
    private static Method findSingleArgumentMethod(Class<?> configClass, String name) {
        for (Method method : configClass.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1) {
                return method;
            }
        }

        return null;
    }

    /**
     * 判断目标模组当前配置是否无法由世界时间控制。
     *
     * <p>当 Fabric Seasons 锁定季节，或改为跟随真实系统时间时，改 {@code dayTime}
     * 都不会产生预期效果。此时直接跳过同步，让目标模组继续按自己的配置运行。
     *
     * @param config Fabric Seasons / Forge Seasons 配置对象
     * @return 如果当前配置不适合由本模组接管，返回 {@code true}
     * @throws ReflectiveOperationException 当反射调用失败时抛出
     */
    private static boolean isUncontrollable(Object config) throws ReflectiveOperationException {
        return (boolean) isSeasonLockedMethod.invoke(config)
                || (boolean) isSeasonTiedWithSystemTimeMethod.invoke(config);
    }

    /**
     * 判断目标模组是否在主世界启用季节。
     *
     * @param config Fabric Seasons / Forge Seasons 配置对象
     * @param overworld 当前服务器主世界
     * @return 如果目标模组认为主世界可以计算季节，返回 {@code true}
     * @throws ReflectiveOperationException 当反射调用失败时抛出
     */
    private static boolean isValidInDimension(Object config, ServerLevel overworld) throws ReflectiveOperationException {
        if (isValidInDimensionMethod == null) {
            return true;
        }

        return (boolean) isValidInDimensionMethod.invoke(config, overworld.dimension());
    }

    /**
     * 读取目标模组配置中的四季长度。
     *
     * <p>Fabric Seasons / Forge Seasons 的长度单位是 tick，默认每季 672000 tick，
     * 也就是 28 个原版 Minecraft 天。
     *
     * @param config Fabric Seasons / Forge Seasons 配置对象
     * @return 按春、夏、秋、冬顺序排列的每季 tick 长度
     * @throws ReflectiveOperationException 当反射调用失败时抛出
     */
    private static long[] seasonLengths(Object config) throws ReflectiveOperationException {
        return new long[] {
                (int) getSpringLengthMethod.invoke(config),
                (int) getSummerLengthMethod.invoke(config),
                (int) getFallLengthMethod.invoke(config),
                (int) getWinterLengthMethod.invoke(config)
        };
    }

    /**
     * 判断四季长度是否全部有效。
     *
     * @param seasonLengths 按春、夏、秋、冬顺序排列的每季 tick 长度
     * @return 如果四个季节长度都大于 0，返回 {@code true}
     */
    private static boolean hasValidSeasonLengths(long[] seasonLengths) {
        for (long seasonLength : seasonLengths) {
            if (seasonLength <= 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * 读取目标模组配置中的开局季节序号。
     *
     * @param config Fabric Seasons / Forge Seasons 配置对象
     * @return 开局季节序号，0 为春、1 为夏、2 为秋、3 为冬
     * @throws ReflectiveOperationException 当反射调用失败时抛出
     */
    private static int startingSeasonIndex(Object config) throws ReflectiveOperationException {
        Object startingSeason = getStartingSeasonMethod.invoke(config);
        if (startingSeason instanceof Enum<?> startingSeasonEnum) {
            return Math.floorMod(startingSeasonEnum.ordinal(), SEASON_COUNT);
        }

        return 0;
    }

    /**
     * 计算目标模组一年的总 tick 长度。
     *
     * @param seasonLengths 按春、夏、秋、冬顺序排列的每季 tick 长度
     * @return 一年总 tick 数
     */
    private static long yearLength(long[] seasonLengths) {
        long total = 0;
        for (long seasonLength : seasonLengths) {
            total += seasonLength;
        }

        return total;
    }

    /**
     * 将本模组日期换算为 Fabric Seasons / Forge Seasons 的一年循环内 tick。
     *
     * @param date 本模组当前日期
     * @param overworld 当前服务器主世界，用来读取当前日内 tick
     * @param seasonLengths 目标模组按春、夏、秋、冬排列的每季 tick 长度
     * @param startingSeasonIndex 目标模组配置中的开局季节序号
     * @return 目标模组一年循环内的 tick 位置
     */
    private static long toFabricSeasonCycleTicks(
            CalendarDate date,
            ServerLevel overworld,
            long[] seasonLengths,
            int startingSeasonIndex) {
        int targetSeasonIndex = date.seasonIndex();
        long seasonStartTicks = seasonStartTicks(seasonLengths, startingSeasonIndex, targetSeasonIndex);
        long scaledTicksInSeason = scaleSeasonProgress(date, overworld, seasonLengths[targetSeasonIndex]);

        return seasonStartTicks + scaledTicksInSeason;
    }

    /**
     * 计算目标季节在目标模组一年循环内的起始 tick。
     *
     * <p>Fabric Seasons 允许配置开局季节。若开局季节是冬，则世界时间 0 对应冬季，
     * 春季的起点就需要先跳过冬季长度。
     *
     * @param seasonLengths 目标模组按春、夏、秋、冬排列的每季 tick 长度
     * @param startingSeasonIndex 目标模组配置中的开局季节序号
     * @param targetSeasonIndex 本模组当前要同步到的季节序号
     * @return 目标季节在一年循环内的起始 tick
     */
    private static long seasonStartTicks(long[] seasonLengths, int startingSeasonIndex, int targetSeasonIndex) {
        long startTicks = 0;
        int seasonIndex = startingSeasonIndex;

        while (seasonIndex != targetSeasonIndex) {
            startTicks += seasonLengths[seasonIndex];
            seasonIndex = (seasonIndex + 1) % SEASON_COUNT;
        }

        return startTicks;
    }

    /**
     * 把本模组的大季节进度缩放到目标模组当前季节长度内。
     *
     * <p>这里把“季节内第几天”和“当前日内 tick”一起缩放，避免只按整天映射时，
     * 在最后一天的后半段提前越过目标季节边界。
     *
     * @param date 本模组当前日期
     * @param overworld 当前服务器主世界，用来读取当前日内 tick
     * @param targetSeasonLength 目标模组当前季节 tick 长度
     * @return 目标季节内的 0 基 tick 进度
     */
    private static long scaleSeasonProgress(CalendarDate date, ServerLevel overworld, long targetSeasonLength) {
        long sourceSeasonLength = (long) CalendarDate.daysPerSeason() * Level.TICKS_PER_DAY;
        long sourceProgress = ((long) date.dayInSeason() * Level.TICKS_PER_DAY) + currentDayTicks(overworld);
        long scaledProgress = (sourceProgress * targetSeasonLength) / sourceSeasonLength;

        return Math.min(scaledProgress, targetSeasonLength - 1);
    }

    /**
     * 把目标循环 tick 放回当前世界时间所在的季节年。
     *
     * <p>普通日推进时，按当前季节年对齐可以减少世界时间跳动；当目标循环位置从冬末回到春初时，
     * 如果差值超过半个目标年份，就顺延到下一年，避免自然跨年时把世界时间大幅倒退。
     *
     * @param currentDayTime 当前主世界 dayTime
     * @param yearLength 目标模组一年 tick 长度
     * @param targetCycleTicks 目标一年循环内 tick
     * @return 需要写入世界的绝对 dayTime
     */
    private static long alignToCurrentSeasonYear(long currentDayTime, long yearLength, long targetCycleTicks) {
        long currentYear = Math.floorDiv(currentDayTime, yearLength);
        long currentCycleTicks = Math.floorMod(currentDayTime, yearLength);
        long targetDayTime = (currentYear * yearLength) + targetCycleTicks;

        if (targetCycleTicks < currentCycleTicks && currentCycleTicks - targetCycleTicks > yearLength / 2) {
            targetDayTime += yearLength;
        }

        return targetDayTime;
    }

    /**
     * 将所有维度的世界时间同步到同一个目标值。
     *
     * <p>原版 {@code /time set} 也会更新所有维度。这里保持同样做法，避免主世界和其他维度
     * 在安装 Fabric Seasons / Forge Seasons 后出现季节计算不一致。
     *
     * @param overworld 当前服务器主世界
     * @param targetDayTime 目标世界时间
     * @return 如果至少一个维度被改动，返回 {@code true}
     */
    private static boolean setAllLevelDayTimes(ServerLevel overworld, long targetDayTime) {
        boolean changed = false;

        for (ServerLevel level : overworld.getServer().getAllLevels()) {
            if (level.getDayTime() != targetDayTime) {
                level.setDayTime(targetDayTime);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * 计算当前 Minecraft 日内 tick。
     *
     * @param overworld 当前服务器主世界
     * @return 当前日内 tick，范围约为 0-23999
     */
    private static long currentDayTicks(ServerLevel overworld) {
        return Math.floorMod(overworld.getDayTime(), Level.TICKS_PER_DAY);
    }
}
