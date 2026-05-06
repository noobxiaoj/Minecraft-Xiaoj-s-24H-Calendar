package com.xiaoj.xiaoj24hcalendar.calendar;

/**
 * 表示模组内部的无年份日期。
 *
 * <p>当前规则固定为一年 4 个月、每月 30 天，但这个类不会保存年份。
 * 当日期超过第 4 月第 30 日后，会直接回到第 1 月第 1 日，适合作为
 * 早期版本的虚拟循环日历基础。
 *
 * @param month 当前月份，合法范围是 1 到 {@link #MONTHS_PER_CYCLE}
 * @param day 当前日期，合法范围是 1 到 {@link #DAYS_PER_MONTH}
 */
public record CalendarDate(int month, int day) {

    /** 一个循环中的月份数量；暂定为 4，用来对应后续春夏秋冬四季。 */
    public static final int MONTHS_PER_CYCLE = 4;

    /** 每个月的天数；暂定每月 30 天。 */
    public static final int DAYS_PER_MONTH = 30;

    /** 一个完整循环的总天数，不记录年份，只用于月日换算。 */
    public static final int DAYS_PER_CYCLE = MONTHS_PER_CYCLE * DAYS_PER_MONTH;

    /** 默认开局日期。 */
    public static final CalendarDate DEFAULT = new CalendarDate(1, 1);

    /**
     * 构造并校验日期。
     *
     * @param month 当前月份，必须在 1 到 4 之间
     * @param day 当前日期，必须在 1 到 30 之间
     * @throws IllegalArgumentException 当传入日期超出日历范围时抛出
     */
    public CalendarDate {
        if (!isValid(month, day)) {
            throw new IllegalArgumentException("Invalid calendar date: month=" + month + ", day=" + day);
        }
    }

    /**
     * 判断月日组合是否在当前日历规则内。
     *
     * @param month 要检查的月份
     * @param day 要检查的日期
     * @return 如果月份为 1-4 且日期为 1-30，则返回 {@code true}
     */
    public static boolean isValid(int month, int day) {
        return month >= 1
                && month <= MONTHS_PER_CYCLE
                && day >= 1
                && day <= DAYS_PER_MONTH;
    }

    /**
     * 在当前日期基础上推进指定天数。
     *
     * <p>这里使用循环日历：超过第 4 月第 30 日会回到第 1 月第 1 日。
     * {@code days} 可以为负数，方便后续命令或测试回退日期。
     *
     * @param days 要推进的天数，正数前进、负数后退、0 保持不变
     * @return 推进后的新日期对象
     */
    public CalendarDate addDays(int days) {
        int currentIndex = toCycleIndex();
        int nextIndex = Math.floorMod(currentIndex + days, DAYS_PER_CYCLE);

        return fromCycleIndex(nextIndex);
    }

    /**
     * 将日期转换为循环内的 0 基序号。
     *
     * @return 第 1 月第 1 日为 0，第 4 月第 30 日为 119
     */
    public int toCycleIndex() {
        return ((month - 1) * DAYS_PER_MONTH) + (day - 1);
    }

    /**
     * 根据循环内序号创建日期。
     *
     * @param index 循环内 0 基序号，允许超出范围，会自动取模
     * @return 对应的无年份日期
     */
    public static CalendarDate fromCycleIndex(int index) {
        int normalizedIndex = Math.floorMod(index, DAYS_PER_CYCLE);
        int month = (normalizedIndex / DAYS_PER_MONTH) + 1;
        int day = (normalizedIndex % DAYS_PER_MONTH) + 1;

        return new CalendarDate(month, day);
    }
}
