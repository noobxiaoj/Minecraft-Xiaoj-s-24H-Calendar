package com.xiaoj.xiaoj24hcalendar.calendar;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import com.xiaoj.xiaoj24hcalendar.Xiaoj24HCalendar;
import com.xiaoj.xiaoj24hcalendar.config.CalendarConfig;

/**
 * 保存当前世界的虚拟日历数据。
 *
 * <p>这个存档数据只保存“月/日”，不会保存年份。为了知道 Minecraft 主世界是否跨过了
 * 新的一天，这里额外保存最近一次观察到的主世界日计数；该计数只作为推进日期的内部
 * 游标，不作为日历年份对外展示。
 */
public class CalendarSavedData extends SavedData {

    /** 存档文件名，最终会保存为 {@code data/xiaoj_24h_calendar_calendar.dat}。 */
    private static final String DATA_NAME = Xiaoj24HCalendar.MOD_ID + "_calendar";

    /** NBT 字段：月份。 */
    private static final String TAG_MONTH = "Month";

    /** NBT 字段：日期。 */
    private static final String TAG_DAY = "Day";

    /** NBT 字段：最近观察到的 Minecraft 主世界日计数。 */
    private static final String TAG_LAST_OBSERVED_DAY = "LastObservedMinecraftDay";

    /** 尚未绑定世界日计数时使用的哨兵值。 */
    private static final long UNINITIALIZED_DAY = Long.MIN_VALUE;

    /** 当前虚拟日期，只有月和日。 */
    private CalendarDate date = CalendarDate.DEFAULT;

    /** 最近一次观察到的主世界天数，用于判断是否需要推进日期。 */
    private long lastObservedMinecraftDay = UNINITIALIZED_DAY;

    /**
     * 创建 SavedData 工厂。
     *
     * @return Minecraft 数据存储系统使用的工厂对象
     */
    public static SavedData.Factory<CalendarSavedData> factory() {
        return new SavedData.Factory<>(CalendarSavedData::new, CalendarSavedData::load, null);
    }

    /**
     * 从服务器主世界获取日历数据。
     *
     * @param server 当前 Minecraft 服务端
     * @return 当前世界的日历存档数据
     */
    public static CalendarSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    /**
     * 从 NBT 读取日历数据。
     *
     * @param tag 存档 NBT
     * @param lookupProvider 注册表查询对象，当前日历数据不需要使用
     * @return 反序列化后的日历数据
     */
    private static CalendarSavedData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        CalendarSavedData data = new CalendarSavedData();

        int month = tag.getInt(TAG_MONTH);
        int day = tag.getInt(TAG_DAY);
        if (CalendarDate.isValid(month, day)) {
            data.date = new CalendarDate(month, day);
        } else if (month >= 1 && day >= 1 && day <= CalendarDate.DAYS_PER_MONTH) {
            data.date = CalendarDate.fromStoredDate(month, day);
        }

        if (tag.contains(TAG_LAST_OBSERVED_DAY)) {
            data.lastObservedMinecraftDay = tag.getLong(TAG_LAST_OBSERVED_DAY);
        }

        return data;
    }

    /**
     * 保存日历数据到 NBT。
     *
     * @param tag 要写入的 NBT
     * @param lookupProvider 注册表查询对象，当前日历数据不需要使用
     * @return 写入后的 NBT
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        tag.putInt(TAG_MONTH, date.month());
        tag.putInt(TAG_DAY, date.day());
        tag.putLong(TAG_LAST_OBSERVED_DAY, lastObservedMinecraftDay);

        return tag;
    }

    /**
     * 获取当前无年份日期。
     *
     * @return 当前日期
     */
    public CalendarDate date() {
        if (!CalendarDate.isValid(date.month(), date.day())) {
            date = CalendarDate.fromStoredDate(date.month(), date.day());
            setDirty();
        }

        return date;
    }

    /**
     * 根据主世界时间推进日历日期。
     *
     * <p>只有主世界被用于推进日历，因为后续 BetterDays 也主要管理主世界时间。
     * 如果玩家或其他模组把时间调回过去，这里不会倒退日历，只会重置内部观察游标，
     * 避免下一次跨天计算出现大幅负数。
     *
     * @param level 当前主世界
     * @return 如果本次调用推进了日期，返回 {@code true}
     */
    public boolean updateFromOverworld(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return false;
        }

        long currentMinecraftDay = Math.floorDiv(level.getDayTime(), Level.TICKS_PER_DAY);

        if (!CalendarConfig.enableMonths()) {
            if (lastObservedMinecraftDay != currentMinecraftDay) {
                lastObservedMinecraftDay = currentMinecraftDay;
                setDirty();
            }
            return false;
        }

        if (lastObservedMinecraftDay == UNINITIALIZED_DAY) {
            lastObservedMinecraftDay = currentMinecraftDay;
            setDirty();
            return false;
        }

        long elapsedDays = currentMinecraftDay - lastObservedMinecraftDay;
        if (elapsedDays <= 0) {
            if (elapsedDays < 0) {
                lastObservedMinecraftDay = currentMinecraftDay;
                setDirty();
            }
            return false;
        }

        date = date.addDays(saturatedLongToInt(elapsedDays));
        lastObservedMinecraftDay = currentMinecraftDay;
        setDirty();

        return true;
    }

    /**
     * 设置当前日期，并把跨天观察游标同步到当前主世界时间。
     *
     * @param newDate 要设置的新日期
     * @param overworld 当前主世界，用来避免设置日期后同一 tick 被错误推进
     */
    public void setDate(CalendarDate newDate, ServerLevel overworld) {
        date = newDate;
        rebindObservedDay(overworld);
        setDirty();
    }

    /**
     * 在当前日期基础上推进指定天数，并同步内部观察游标。
     *
     * @param days 要推进的天数
     * @param overworld 当前主世界，用来避免命令执行后同一 tick 被重复推进
     */
    public void addDays(int days, ServerLevel overworld) {
        setDate(date.addDays(days), overworld);
    }

    /**
     * 把内部跨天观察游标重新绑定到当前主世界时间。
     *
     * <p>大多数季节兼容层只写外部模组自己的存档，不会影响原版世界时间；但
     * Fabric Seasons / Forge Seasons 的季节直接由世界 {@code dayTime} 推导，
     * 因此兼容层必须调整世界时间。调整后调用这个方法，可以避免下一 tick 把这次
     * 时间跳转误判成本模组日期需要额外推进或回退。
     *
     * @param overworld 当前主世界
     */
    public void rebindObservedDay(ServerLevel overworld) {
        lastObservedMinecraftDay = Math.floorDiv(overworld.getDayTime(), Level.TICKS_PER_DAY);
        setDirty();
    }

    /**
     * 把 long 天数安全压缩为 int。
     *
     * <p>正常游戏里一次 tick 不会跨过超过 int 范围的天数；这里仍然做饱和转换，
     * 是为了避免外部模组或手动编辑存档造成极端数值时溢出。
     *
     * @param value 要转换的 long 值
     * @return int 范围内的饱和值
     */
    private static int saturatedLongToInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }
}
