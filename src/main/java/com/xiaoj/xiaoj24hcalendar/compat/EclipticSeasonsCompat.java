package com.xiaoj.xiaoj24hcalendar.compat;

import java.lang.reflect.Method;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import com.xiaoj.xiaoj24hcalendar.calendar.CalendarDate;

/**
 * Ecliptic Seasons 的反射兼容层。
 *
 * <p>映射规则由本模组掌控：一个完整日历循环始终拆成 24 个节气。4 月制下每节气 5 天，
 * 12 月制下每节气 15 天。Ecliptic Seasons 自己的“每节气持续天数”仍可能被玩家配置成
 * 7 天或其他值，因此写入时会把本模组节气进度缩放到对方配置的节气长度里。
 */
final class EclipticSeasonsCompat {

    /** Ecliptic Seasons 保存服务端节气状态的类。 */
    private static final String SOLAR_DATA_MANAGER_CLASS =
            "com.teamtea.eclipticseasons.common.core.solar.SolarDataManager";

    /** 反射缓存：SolarDataManager.get(ServerLevel)。 */
    private static Method getDataMethod;

    /** 反射缓存：SolarDataManager.getSolarTermLastingDays()。 */
    private static Method getSolarTermLastingDaysMethod;

    /** 反射缓存：SolarDataManager.setSolarTermsDay(int)。 */
    private static Method setSolarTermsDayMethod;

    /** 反射缓存：SolarDataManager.setSolarTermsTicks(int)。 */
    private static Method setSolarTermsTicksMethod;

    /** 反射缓存：SolarDataManager.sendAndUpdate(ServerLevel)。 */
    private static Method sendAndUpdateMethod;

    private EclipticSeasonsCompat() {
    }

    /**
     * 把本模组日期同步到 Ecliptic Seasons。
     *
     * @param overworld 当前服务器主世界
     * @param date 本模组当前日期
     * @throws ReflectiveOperationException 当目标模组 API/内部结构变化导致反射失败时抛出
     */
    static void sync(ServerLevel overworld, CalendarDate date) throws ReflectiveOperationException {
        ensureInitialized();

        Object solarData = getDataMethod.invoke(null, overworld);
        int lastingDays = Math.max(1, (int) getSolarTermLastingDaysMethod.invoke(solarData));
        int targetSolarTermsDay = toEclipticSolarTermsDay(date, lastingDays);

        setSolarTermsDayMethod.invoke(solarData, targetSolarTermsDay);
        setSolarTermsTicksMethod.invoke(solarData, currentDayTicks(overworld));
        sendAndUpdateMethod.invoke(solarData, overworld);
    }

    /**
     * 懒加载 Ecliptic Seasons 的反射入口。
     *
     * <p>这些方法只在检测到目标模组已加载后才会初始化，因此不会影响未安装
     * Ecliptic Seasons 的普通游戏环境。
     *
     * @throws ReflectiveOperationException 当类或方法不存在时抛出
     */
    private static void ensureInitialized() throws ReflectiveOperationException {
        if (getDataMethod != null) {
            return;
        }

        Class<?> solarDataManagerClass = Class.forName(SOLAR_DATA_MANAGER_CLASS);
        getDataMethod = solarDataManagerClass.getMethod("get", ServerLevel.class);
        getSolarTermLastingDaysMethod = solarDataManagerClass.getMethod("getSolarTermLastingDays");
        setSolarTermsDayMethod = solarDataManagerClass.getMethod("setSolarTermsDay", int.class);
        setSolarTermsTicksMethod = solarDataManagerClass.getMethod("setSolarTermsTicks", int.class);
        sendAndUpdateMethod = solarDataManagerClass.getMethod("sendAndUpdate", ServerLevel.class);
    }

    /**
     * 将本模组日期换算为 Ecliptic Seasons 的 SolarTermsDay。
     *
     * @param date 本模组当前日期
     * @param lastingDays Ecliptic Seasons 当前配置的每节气天数
     * @return Ecliptic Seasons 使用的累计节气日数
     */
    private static int toEclipticSolarTermsDay(CalendarDate date, int lastingDays) {
        int termIndex = date.solarTermIndex();
        int scaledDayInTerm = scaleProgress(
                date.dayInSolarTerm(),
                CalendarDate.daysPerSolarTerm(),
                lastingDays);

        return (termIndex * lastingDays) + scaledDayInTerm;
    }

    /**
     * 把源进度按比例缩放到目标长度内。
     *
     * <p>例如本模组 5 天节气映射到 Ecliptic 默认 7 天节气时，第 1-5 天会落到
     * 0、1、2、4、5。最后一天不会越过 6，避免提前进入下一个节气。
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

    /**
     * 计算当前 Minecraft 日内 tick。
     *
     * <p>Ecliptic Seasons 会用这个值判断是否跨过一天。同步日期时一起写入日内 tick，
     * 可以避免玩家用命令调整时间后，目标模组在下一 tick 误判出额外的一天。
     *
     * @param overworld 当前服务器主世界
     * @return 当前日内 tick，范围约为 0-23999
     */
    private static int currentDayTicks(ServerLevel overworld) {
        return Math.floorMod(overworld.getDayTime(), Level.TICKS_PER_DAY);
    }
}
