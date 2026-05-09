package com.xiaoj.xiaoj24hcalendar.calendar;

import com.xiaoj.xiaoj24hcalendar.config.CalendarConfig;

/**
 * 表示模组内部的无年份日期。
 *
 * <p>当前规则支持 4 月制和 12 月制，每月固定 30 天，但这个类不会保存年份。
 * 当日期超过当前配置下的最后一天后，会直接回到第 1 月第 1 日，适合作为
 * 早期版本的虚拟循环日历基础。
 *
 * @param month 当前月份，合法范围是 1 到 {@link #monthsPerCycle()}
 * @param day 当前日期，合法范围是 1 到 {@link #DAYS_PER_MONTH}
 */
public record CalendarDate(int month, int day) {

    /** 每个月的天数；4 月制和 12 月制都固定为每月 30 天。 */
    public static final int DAYS_PER_MONTH = 30;

    /** 普通四季一轮共有 4 个季节。 */
    public static final int SEASONS_PER_CYCLE = 4;

    /** Ecliptic Seasons 一轮共有 24 个节气。 */
    public static final int SOLAR_TERMS_PER_CYCLE = 24;

    /** Serene Seasons 普通四季一轮共有 12 个小季节。 */
    public static final int SUB_SEASONS_PER_CYCLE = 12;

    /**
     * 4 月制下的月份显示名称。
     *
     * <p>数组下标从 0 开始，而存档和命令参数中的月份从 1 开始，因此读取时需要使用
     * {@code month - 1} 进行转换。这里把名称放在日期模型里，避免命令、事件或后续 UI
     * 各自维护一份月份文案导致显示不一致。
     */
    private static final String[] FOUR_MONTH_NAMES = {
            "芳花月",
            "荷风月",
            "枫落月",
            "霜雪月"
    };

    /** 四季显示名称，顺序和本模组季节序号保持一致：春、夏、秋、冬。 */
    private static final String[] SEASON_NAMES = {
            "春",
            "夏",
            "秋",
            "冬"
    };

    /**
     * Ecliptic Seasons 二十四节气显示名称。
     *
     * <p>数组顺序与 Ecliptic Seasons 的 {@code SolarTerm} 枚举顺序保持一致。兼容层写入
     * 目标模组时使用同一个序号，因此命令展示和实际同步目标会保持一致。
     */
    private static final String[] SOLAR_TERM_NAMES = {
            "立春",
            "雨水",
            "惊蛰",
            "春分",
            "清明",
            "谷雨",
            "立夏",
            "小满",
            "芒种",
            "夏至",
            "小暑",
            "大暑",
            "立秋",
            "处暑",
            "白露",
            "秋分",
            "寒露",
            "霜降",
            "立冬",
            "小雪",
            "大雪",
            "冬至",
            "小寒",
            "大寒"
    };

    /**
     * Serene Seasons 小季节显示名称。
     *
     * <p>数组顺序与 Serene Seasons 的 {@code SubSeason} 枚举顺序保持一致。命令输出保持简短，
     * 因此这里只保留中文小季节名。
     */
    private static final String[] SUB_SEASON_NAMES = {
            "初春",
            "仲春",
            "暮春",
            "初夏",
            "仲夏",
            "暮夏",
            "初秋",
            "仲秋",
            "暮秋",
            "初冬",
            "仲冬",
            "暮冬"
    };

    /** 默认开局日期。 */
    public static final CalendarDate DEFAULT = new CalendarDate(1, 1);

    /**
     * 构造并校验日期。
     *
     * @param month 当前月份，必须在 1 到当前配置的月份数量之间
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
     * @return 如果月份和日期都落在当前配置的日历范围内，则返回 {@code true}
     */
    public static boolean isValid(int month, int day) {
        return month >= 1
                && month <= monthsPerCycle()
                && day >= 1
                && day <= DAYS_PER_MONTH;
    }

    /**
     * 获取指定月份的显示名称。
     *
     * @param month 要显示的月份，合法范围是 1 到当前配置的月份数量
     * @return 4 月制返回中文月份名；12 月制返回数字字符串
     * @throws IllegalArgumentException 当月份超出当前配置范围时抛出
     */
    public static String monthName(int month) {
        if (month < 1 || month > monthsPerCycle()) {
            throw new IllegalArgumentException("Invalid calendar month: " + month);
        }

        if (CalendarConfig.useTwelveMonths()) {
            return Integer.toString(month);
        }

        return FOUR_MONTH_NAMES[month - 1];
    }

    /**
     * 获取当前日期所属月份的显示名称。
     *
     * @return 当前月份对应的中文名称
     */
    public String monthName() {
        return monthName(month);
    }

    /**
     * 获取当前配置下一个循环中的月份数量。
     *
     * @return 4 月制返回 4，12 月制返回 12
     */
    public static int monthsPerCycle() {
        return CalendarConfig.monthsPerCycle();
    }

    /**
     * 获取当前配置下一个完整循环的总天数。
     *
     * @return 当前循环总天数；4 月制为 120 天，12 月制为 360 天
     */
    public static int daysPerCycle() {
        return monthsPerCycle() * DAYS_PER_MONTH;
    }

    /**
     * 获取当前配置下每个普通季节覆盖的本模组天数。
     *
     * <p>4 月制下每个月就是一个季节，因此每季 30 天；12 月制下每 3 个月是一个季节，
     * 因此每季 90 天。Fabric Seasons / Forge Seasons 只有春夏秋冬四个大季节，
     * 兼容层会用这个长度来计算要同步到对方的季节进度。
     *
     * @return 每个普通季节对应的本模组天数
     */
    public static int daysPerSeason() {
        return daysPerCycle() / SEASONS_PER_CYCLE;
    }

    /**
     * 获取当前配置下每个节气覆盖的本模组天数。
     *
     * <p>4 月制下是 5 天一个节气；12 月制下是一年 360 天平均分给 24 节气，
     * 因此是 15 天一个节气。
     *
     * @return 每个节气对应的本模组天数
     */
    public static int daysPerSolarTerm() {
        return daysPerCycle() / SOLAR_TERMS_PER_CYCLE;
    }

    /**
     * 获取当前配置下每个 Serene Seasons 小季节覆盖的本模组天数。
     *
     * <p>4 月制下是 10 天一个 Early/Mid/Late 段；12 月制下是一年 360 天平均分给
     * 12 个小季节，因此是 30 天一个小季节。
     *
     * @return 每个小季节对应的本模组天数
     */
    public static int daysPerSubSeason() {
        return daysPerCycle() / SUB_SEASONS_PER_CYCLE;
    }

    /**
     * 在当前日期基础上推进指定天数。
     *
     * <p>这里使用循环日历：超过当前配置下最后一天会回到第 1 月第 1 日。
     * {@code days} 可以为负数，方便后续命令或测试回退日期。
     *
     * @param days 要推进的天数，正数前进、负数后退、0 保持不变
     * @return 推进后的新日期对象
     */
    public CalendarDate addDays(int days) {
        int currentIndex = toCycleIndex();
        int nextIndex = Math.floorMod(currentIndex + days, daysPerCycle());

        return fromCycleIndex(nextIndex);
    }

    /**
     * 将日期转换为循环内的 0 基序号。
     *
     * @return 第 1 月第 1 日为 0，循环最后一天为 {@code daysPerCycle() - 1}
     */
    public int toCycleIndex() {
        return ((month - 1) * DAYS_PER_MONTH) + (day - 1);
    }

    /**
     * 将日期转换为循环内的 1 基天数。
     *
     * @return 第 1 月第 1 日为 1，循环最后一天为 {@link #daysPerCycle()}
     */
    public int toCycleDay() {
        return toCycleIndex() + 1;
    }

    /**
     * 计算当前日期对应 Ecliptic Seasons 的节气序号。
     *
     * <p>本模组会把一个完整循环平均拆成 24 个节气。4 月制下是每 5 天一个节气，
     * 12 月制下是每 15 天一个节气。
     * 序号从 0 开始，与 Ecliptic Seasons 的 {@code SolarTerm} 枚举顺序保持一致：
     * 0 为立春，23 为大寒。
     *
     * @return 当前日期所在的节气序号，范围为 0-23
     */
    public int solarTermIndex() {
        return toCycleIndex() / daysPerSolarTerm();
    }

    /**
     * 计算当前日期在当前节气段内的第几天。
     *
     * <p>返回值从 0 开始，例如某个节气的第 1 天返回 0。
     * 兼容层会把这个进度缩放到 Ecliptic Seasons 自己配置的节气长度中。
     *
     * @return 当前日期在节气段内的 0 基天数
     */
    public int dayInSolarTerm() {
        return toCycleIndex() % daysPerSolarTerm();
    }

    /**
     * 计算当前日期对应的四季序号。
     *
     * <p>当前循环被平均拆成 4 个季节。4 月制下每个月就是一个季节；12 月制下每 3 个月
     * 是一个季节。
     *
     * @return 当前季节序号，0 为春季，3 为冬季
     */
    public int seasonIndex() {
        return toCycleIndex() / daysPerSeason();
    }

    /**
     * 计算当前日期在普通季节段内的第几天。
     *
     * <p>返回值从 0 开始，例如春季第 1 天返回 0。Fabric Seasons / Forge Seasons
     * 没有“小季节”状态，只能把本模组的大季节进度缩放到它自己的每季 tick 长度里。
     *
     * @return 当前日期在普通季节段内的 0 基天数
     */
    public int dayInSeason() {
        return toCycleIndex() % daysPerSeason();
    }

    /**
     * 获取当前日期对应的四季名称。
     *
     * @return 当前四季名称，例如“春”
     */
    public String seasonName() {
        return SEASON_NAMES[seasonIndex()];
    }

    /**
     * 获取普通四季风格的简短显示。
     *
     * <p>Fabric Seasons / Forge Seasons 不提供节气或 Early/Mid/Late 小季节，所以命令输出
     * 只显示大季节，例如“春”。
     *
     * @return 当前四季名称，例如“春”
     */
    public String seasonSummary() {
        return seasonName();
    }

    /**
     * 获取当前日期对应的 Ecliptic Seasons 节气名称。
     *
     * @return 当前节气名称，例如“立春”
     */
    public String solarTermName() {
        return SOLAR_TERM_NAMES[solarTermIndex()];
    }

    /**
     * 获取 Ecliptic Seasons 风格的简短季节显示。
     *
     * @return 节气和四季组合，例如“立春(春)”
     */
    public String solarTermSummary() {
        return solarTermName() + "(" + seasonName() + ")";
    }

    /**
     * 计算当前日期对应 Serene Seasons 的小季节序号。
     *
     * <p>当前循环会被平均拆成 12 个 Early/Mid/Late 小季节。4 月制下每段 10 天，
     * 12 月制下每段 30 天。
     * 序号从 0 开始，与 Serene Seasons 的 {@code SubSeason} 枚举顺序保持一致：
     * 0 为 Early Spring，11 为 Late Winter。
     *
     * @return 当前日期所在的小季节序号，范围为 0-11
     */
    public int subSeasonIndex() {
        return toCycleIndex() / daysPerSubSeason();
    }

    /**
     * 计算当前日期在当前小季节段内的第几天。
     *
     * <p>返回值从 0 开始，例如 Early/Mid/Late 段的第 1 天返回 0。
     * 兼容层会把这个进度缩放到 Serene Seasons 自己配置的 sub-season 长度中。
     *
     * @return 当前日期在小季节段内的 0 基天数
     */
    public int dayInSubSeason() {
        return toCycleIndex() % daysPerSubSeason();
    }

    /**
     * 获取当前日期对应的 Serene Seasons 小季节名称。
     *
     * @return 当前小季节名称，例如“初春”
     */
    public String subSeasonName() {
        return SUB_SEASON_NAMES[subSeasonIndex()];
    }

    /**
     * 获取 Serene Seasons 风格的简短季节显示。
     *
     * @return 小季节和四季组合，例如“初春(春)”
     */
    public String subSeasonSummary() {
        return subSeasonName() + "(" + seasonName() + ")";
    }

    /**
     * 根据循环内序号创建日期。
     *
     * @param index 循环内 0 基序号，允许超出范围，会自动取模
     * @return 对应的无年份日期
     */
    public static CalendarDate fromCycleIndex(int index) {
        int normalizedIndex = Math.floorMod(index, daysPerCycle());
        int month = (normalizedIndex / DAYS_PER_MONTH) + 1;
        int day = (normalizedIndex % DAYS_PER_MONTH) + 1;

        return new CalendarDate(month, day);
    }

    /**
     * 从存档中的月/日恢复日期，并按当前配置归一化。
     *
     * <p>玩家可能在同一个世界中从 12 月制切回 4 月制。此时存档里可能存在第 8 月这样的日期；
     * 这个方法会先把存档月/日换算成循环日序号，再按当前配置取模，避免旧配置日期让世界无法读取。
     *
     * @param storedMonth 存档中的月份
     * @param storedDay 存档中的日期
     * @return 当前配置下合法的日期
     */
    public static CalendarDate fromStoredDate(int storedMonth, int storedDay) {
        int rawIndex = ((storedMonth - 1) * DAYS_PER_MONTH) + (storedDay - 1);
        return fromCycleIndex(rawIndex);
    }
}
