package com.xiaoj.xiaoj24hcalendar.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import com.xiaoj.xiaoj24hcalendar.calendar.CalendarDate;

/**
 * Serene Seasons 的反射兼容层。
 *
 * <p>映射规则由本模组掌控：一个完整日历循环始终拆成 12 个 Early/Mid/Late 小季节。
 * 4 月制下每段 10 天，12 月制下每段 30 天。Serene Seasons 自己的 sub-season 长度可配置，
 * 因此写入时会把本模组进度缩放到对方当前配置的 sub-season tick 长度里。
 */
final class SereneSeasonsCompat {

    /** Serene Seasons 的季节处理类，包含读取存档和发送同步包的方法。 */
    private static final String SEASON_HANDLER_CLASS = "sereneseasons.season.SeasonHandler";

    /** Serene Seasons 的季节时间类，包含 sub-season 时长计算。 */
    private static final String SEASON_TIME_CLASS = "sereneseasons.season.SeasonTime";

    /** 反射缓存：SeasonHandler.getSeasonSavedData(Level)。 */
    private static Method getSeasonSavedDataMethod;

    /** 反射缓存：SeasonHandler.sendSeasonUpdate(Level)。 */
    private static Method sendSeasonUpdateMethod;

    /** 反射缓存：SeasonSavedData.seasonCycleTicks。 */
    private static Field seasonCycleTicksField;

    /** 反射缓存：SavedData.setDirty()。 */
    private static Method setDirtyMethod;

    /** 反射缓存：SeasonTime.ZERO。 */
    private static Object zeroSeasonTime;

    /** 反射缓存：SeasonTime.getSubSeasonDuration()。 */
    private static Method getSubSeasonDurationMethod;

    private SereneSeasonsCompat() {
    }

    /**
     * 把本模组日期同步到 Serene Seasons。
     *
     * @param overworld 当前服务器主世界
     * @param date 本模组当前日期
     * @throws ReflectiveOperationException 当目标模组 API/内部结构变化导致反射失败时抛出
     */
    static void sync(ServerLevel overworld, CalendarDate date) throws ReflectiveOperationException {
        ensureInitialized();

        Object seasonSavedData = getSeasonSavedDataMethod.invoke(null, overworld);
        int subSeasonDuration = Math.max(1, (int) getSubSeasonDurationMethod.invoke(zeroSeasonTime));
        int targetCycleTicks = toSereneSeasonCycleTicks(date, subSeasonDuration);

        seasonCycleTicksField.setInt(seasonSavedData, targetCycleTicks);
        setDirtyMethod.invoke(seasonSavedData);
        sendSeasonUpdateMethod.invoke(null, overworld);
    }

    /**
     * 懒加载 Serene Seasons 的反射入口。
     *
     * <p>这些入口只会在 Serene Seasons 已加载时初始化，因此玩家不装 Serene Seasons 时
     * 不会因为缺少类而影响本模组启动。
     *
     * @throws ReflectiveOperationException 当类、字段或方法不存在时抛出
     */
    private static void ensureInitialized() throws ReflectiveOperationException {
        if (getSeasonSavedDataMethod != null) {
            return;
        }

        Class<?> seasonHandlerClass = Class.forName(SEASON_HANDLER_CLASS);
        Class<?> seasonTimeClass = Class.forName(SEASON_TIME_CLASS);

        getSeasonSavedDataMethod = seasonHandlerClass.getMethod("getSeasonSavedData", Level.class);
        sendSeasonUpdateMethod = seasonHandlerClass.getMethod("sendSeasonUpdate", Level.class);

        Class<?> seasonSavedDataClass = getSeasonSavedDataMethod.getReturnType();
        seasonCycleTicksField = seasonSavedDataClass.getField("seasonCycleTicks");
        setDirtyMethod = seasonSavedDataClass.getMethod("setDirty");

        zeroSeasonTime = seasonTimeClass.getField("ZERO").get(null);
        getSubSeasonDurationMethod = seasonTimeClass.getMethod("getSubSeasonDuration");
    }

    /**
     * 将本模组日期换算为 Serene Seasons 的 seasonCycleTicks。
     *
     * @param date 本模组当前日期
     * @param subSeasonDuration Serene Seasons 当前配置的每个 sub-season tick 数
     * @return Serene Seasons 使用的一年循环内 tick 数
     */
    private static int toSereneSeasonCycleTicks(CalendarDate date, int subSeasonDuration) {
        int subSeasonIndex = date.subSeasonIndex();
        int dayInSubSeason = date.dayInSubSeason();
        int scaledTicksInSubSeason = scaleProgress(
                dayInSubSeason,
                CalendarDate.daysPerSubSeason(),
                subSeasonDuration);
        scaledTicksInSubSeason = keepLastDayInsideSubSeason(
                scaledTicksInSubSeason,
                dayInSubSeason,
                subSeasonDuration);

        return (subSeasonIndex * subSeasonDuration) + scaledTicksInSubSeason;
    }

    /**
     * 防止 10 天小季节段的最后一天提前越界。
     *
     * <p>Serene Seasons 会在自己的 tick 中按 Minecraft 时间继续推进 {@code seasonCycleTicks}。
     * 如果对方 sub-season 比本模组映射出的天数更短，那么最后一天开头写入的进度不能太靠近
     * 边界，否则当天还没结束就会进入 Mid/Late/下一季。这里把最后一天的起点压到
     * “再走完一个原版日仍不会越界”的位置。
     *
     * @param scaledTicksInSubSeason 原本按比例缩放得到的 sub-season 内 tick
     * @param dayInSubSeason 本模组 10 天段内的 0 基天数
     * @param subSeasonDuration Serene Seasons 当前配置的每个 sub-season tick 数
     * @return 修正后的 sub-season 内 tick
     */
    private static int keepLastDayInsideSubSeason(
            int scaledTicksInSubSeason,
            int dayInSubSeason,
            int subSeasonDuration) {
        if (dayInSubSeason != CalendarDate.daysPerSubSeason() - 1) {
            return scaledTicksInSubSeason;
        }

        int latestSafeStart = subSeasonDuration - Level.TICKS_PER_DAY;
        if (latestSafeStart <= 0) {
            return 0;
        }

        return Math.min(scaledTicksInSubSeason, latestSafeStart);
    }

    /**
     * 把源进度按比例缩放到目标长度内。
     *
     * <p>例如本模组小季节映射到 Serene 默认 8 天 sub-season 时，最后一天不会越过 7，
     * 避免提前进入下一个 sub-season。
     *
     * @param sourceProgress 源段内 0 基进度
     * @param sourceLength 源段总长度
     * @param targetLength 目标段总长度
     * @return 目标段内 0 基进度
     */
    private static int scaleProgress(int sourceProgress, int sourceLength, int targetLength) {
        int scaled = (int) (((long) sourceProgress * targetLength) / sourceLength);
        return Math.min(scaled, targetLength - 1);
    }
}
