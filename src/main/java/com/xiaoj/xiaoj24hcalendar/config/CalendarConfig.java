package com.xiaoj.xiaoj24hcalendar.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Xiaoj's 24H Calendar 的服务端配置。
 *
 * <p>这些配置会注册为 NeoForge 的 {@code SERVER} config，适合控制世界规则类行为。
 * 客户端只负责显示结果，不单独保存一份客户端偏好，避免多人游戏里每个玩家看到不同日历规则。
 */
public final class CalendarConfig {

    /** 4 月制下每个循环的月份数量。 */
    public static final int FOUR_MONTHS_PER_CYCLE = 4;

    /** 12 月制下每个循环的月份数量。 */
    public static final int TWELVE_MONTHS_PER_CYCLE = 12;

    /** 配置规格对象，由模组入口注册到 NeoForge。 */
    public static final ModConfigSpec SPEC;

    /** 是否启用本模组的月份/日期功能；关闭后只保留后续 24H 时间功能入口。 */
    public static final ModConfigSpec.BooleanValue ENABLE_MONTHS;

    /** 是否由本模组接管并同步受支持季节模组的季节进度。 */
    public static final ModConfigSpec.BooleanValue ENABLE_SEASONS;

    /** 是否启用 12 月制；关闭时保持原来的 4 月制。 */
    public static final ModConfigSpec.BooleanValue USE_TWELVE_MONTHS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("calendar");

        ENABLE_MONTHS = builder
                .comment(
                        "Whether to enable this mod's month/date calendar feature.",
                        "When disabled, the calendar date will not advance, date commands are disabled, and only future 24H time features should remain active.",
                        "是否启用本模组的月份/日期功能。关闭后日期不会推进，日期命令不可用，只保留后续 24H 时间功能。")
                .define("enableMonths", true);

        ENABLE_SEASONS = builder
                .comment(
                        "Whether this mod should synchronize supported season mods when exactly one of them is installed.",
                        "When disabled, those season mods are left untouched and continue using their own settings.",
                        "Supported mods: Ecliptic Seasons, Serene Seasons, Fabric Seasons / Forge Seasons.",
                        "If multiple supported season mods are installed, synchronization is disabled to avoid conflicts.",
                        "是否由本模组同步受支持季节模组。关闭后不联动、不干预，季节模组会按自己的配置运行。",
                        "当前支持：Ecliptic Seasons、Serene Seasons、Fabric Seasons / Forge Seasons。",
                        "如果同时安装多个受支持季节模组，会直接禁用季节联动以避免冲突。")
                .define("enableSeasons", true);

        USE_TWELVE_MONTHS = builder
                .comment(
                        "Whether to use a 12-month calendar instead of the default 4-month calendar.",
                        "Each month still has 30 days. In 12-month mode, month names are displayed as numbers.",
                        "是否启用 12 月制。每个月仍然是 30 天；开启后月份名使用数字显示。")
                .define("useTwelveMonths", false);

        builder.pop();

        SPEC = builder.build();
    }

    private CalendarConfig() {
    }

    /**
     * 判断是否启用月份/日期功能。
     *
     * @return 如果本模组应启用月历日期系统，返回 {@code true}
     */
    public static boolean enableMonths() {
        return ENABLE_MONTHS.get();
    }

    /**
     * 判断是否启用季节模组联动。
     *
     * @return 如果本模组应接管并同步外部季节模组进度，返回 {@code true}
     */
    public static boolean enableSeasons() {
        return ENABLE_SEASONS.get();
    }

    /**
     * 判断是否启用 12 月制。
     *
     * @return 如果当前日历应使用 12 个月，返回 {@code true}
     */
    public static boolean useTwelveMonths() {
        return USE_TWELVE_MONTHS.get();
    }

    /**
     * 获取当前配置下一个循环中的月份数量。
     *
     * @return 12 月制开启时返回 12，否则返回 4
     */
    public static int monthsPerCycle() {
        return useTwelveMonths() ? TWELVE_MONTHS_PER_CYCLE : FOUR_MONTHS_PER_CYCLE;
    }
}
