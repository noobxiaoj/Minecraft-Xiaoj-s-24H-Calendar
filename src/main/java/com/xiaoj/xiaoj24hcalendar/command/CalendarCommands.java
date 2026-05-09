package com.xiaoj.xiaoj24hcalendar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import com.xiaoj.xiaoj24hcalendar.calendar.CalendarDate;
import com.xiaoj.xiaoj24hcalendar.calendar.CalendarSavedData;
import com.xiaoj.xiaoj24hcalendar.compat.SeasonCompatManager;
import com.xiaoj.xiaoj24hcalendar.config.CalendarConfig;

/**
 * 注册和执行 {@code /xcalendar} 服务端命令。
 *
 * <p>第一版只提供日期查询和日期调整，不注册 {@code /xcalendar time}、
 * {@code /xcalendar debug}，保持命令面尽量简单。
 */
public final class CalendarCommands {

    /** 命令根节点名称。 */
    private static final String ROOT_COMMAND = "xcalendar";

    /** 修改日期需要的权限等级，和常见管理命令保持一致。 */
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private CalendarCommands() {
    }

    /**
     * 注册日历命令。
     *
     * @param dispatcher Minecraft 命令分发器
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ROOT_COMMAND)
                .then(Commands.literal("date")
                        .executes(context -> showDate(context.getSource())))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                        .then(Commands.argument("month", IntegerArgumentType.integer(1))
                                .then(Commands.argument("day", IntegerArgumentType.integer(1, CalendarDate.DAYS_PER_MONTH))
                                        .executes(context -> setDate(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "month"),
                                                IntegerArgumentType.getInteger(context, "day"))))))
                .then(Commands.literal("adddays")
                        .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                        .then(Commands.argument("days", IntegerArgumentType.integer())
                                .executes(context -> addDays(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "days")))));

        if (shouldRegisterSeasonCommand()) {
            root.then(Commands.literal("season")
                    .executes(context -> showSeason(context.getSource())));
        }

        dispatcher.register(root);
    }

    /**
     * 判断是否应注册 {@code /xcalendar season}。
     *
     * <p>这个命令只在季节联动真的可用时出现：月份功能开启、季节联动开启，并且刚好安装
     * 一个受支持的季节模组。没有支持模组、存在多个支持模组或关闭相关配置时，命令树中
     * 不再出现 {@code season} 子命令。
     *
     * @return 如果当前环境应提供季节查询命令，返回 {@code true}
     */
    private static boolean shouldRegisterSeasonCommand() {
        return CalendarConfig.enableMonths()
                && CalendarConfig.enableSeasons()
                && SeasonCompatManager.loadedSeasonModCount() == 1;
    }

    /**
     * 显示当前日期。
     *
     * @param source 命令来源
     * @return Brigadier 命令返回值
     */
    private static int showDate(CommandSourceStack source) {
        if (!CalendarConfig.enableMonths()) {
            source.sendSuccess(() -> Component.translatable("commands.xiaoj_24h_calendar.calendar.disabled"), false);
            return 1;
        }

        CalendarSavedData data = currentData(source);
        CalendarDate date = data.date();

        source.sendSuccess(() -> dateComponent("commands.xiaoj_24h_calendar.date", date), false);

        return 1;
    }

    /**
     * 显示当前日期对应的季节联动状态。
     *
     * <p>这个命令展示的是本模组会同步给 Ecliptic Seasons、Serene Seasons、
     * Fabric Seasons / Forge Seasons 的目标状态。
     * 如果季节联动关闭，本模组不会读取或覆盖外部季节模组状态，外部季节模组会按自己的配置运行。
     *
     * @param source 命令来源
     * @return Brigadier 命令返回值
     */
    private static int showSeason(CommandSourceStack source) {
        if (!CalendarConfig.enableMonths()) {
            source.sendSuccess(() -> Component.translatable("commands.xiaoj_24h_calendar.calendar.disabled"), false);
            return 1;
        }

        if (!CalendarConfig.enableSeasons()) {
            source.sendSuccess(() -> Component.translatable("commands.xiaoj_24h_calendar.season.disabled"), false);
            return 1;
        }

        if (SeasonCompatManager.hasSeasonCompatConflict()) {
            source.sendSuccess(() -> Component.translatable("commands.xiaoj_24h_calendar.season.conflict"), false);
            return 1;
        }

        CalendarSavedData data = currentData(source);
        CalendarDate date = data.date();

        String seasonSummary = seasonSummary(date);
        if (seasonSummary.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.xiaoj_24h_calendar.season.none"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(seasonSummary), false);

        return 1;
    }

    /**
     * 根据实际安装的季节模组生成简短季节文本。
     *
     * <p>受支持季节模组按互斥模组处理：Ecliptic Seasons 显示节气，例如“立春(春)”；
     * Serene Seasons 显示小季节，例如“初春(春)”；Fabric Seasons / Forge Seasons
     * 没有小季节，只显示“春”这类大季节。
     *
     * @param date 当前本模组日期
     * @return 简短季节文本；没有安装受支持的季节模组时返回空字符串
     */
    private static String seasonSummary(CalendarDate date) {
        if (SeasonCompatManager.loadedSeasonModCount() != 1) {
            return "";
        }

        boolean hasEclipticSeasons = SeasonCompatManager.isEclipticSeasonsLoaded();
        boolean hasSereneSeasons = SeasonCompatManager.isSereneSeasonsLoaded();
        boolean hasFabricSeasons = SeasonCompatManager.isFabricSeasonsLoaded();

        if (hasEclipticSeasons) {
            return date.solarTermSummary();
        }
        if (hasSereneSeasons) {
            return date.subSeasonSummary();
        }
        if (hasFabricSeasons) {
            return date.seasonSummary();
        }

        return "";
    }

    /**
     * 设置当前日期。
     *
     * @param source 命令来源
     * @param month 目标月份
     * @param day 目标日期
     * @return Brigadier 命令返回值
     */
    private static int setDate(CommandSourceStack source, int month, int day) {
        if (!CalendarConfig.enableMonths()) {
            source.sendFailure(Component.translatable("commands.xiaoj_24h_calendar.calendar.disabled"));
            return 0;
        }

        if (!CalendarDate.isValid(month, day)) {
            source.sendFailure(Component.translatable(
                    "commands.xiaoj_24h_calendar.set.invalid",
                    CalendarDate.monthsPerCycle(),
                    CalendarDate.DAYS_PER_MONTH));
            return 0;
        }

        CalendarSavedData data = currentData(source);
        ServerLevel overworld = source.getServer().overworld();
        data.setDate(new CalendarDate(month, day), overworld);
        if (SeasonCompatManager.forceSync(overworld, data.date())) {
            data.rebindObservedDay(overworld);
        }

        source.sendSuccess(() -> dateComponent("commands.xiaoj_24h_calendar.set.success", data.date()), true);

        return 1;
    }

    /**
     * 在当前日期基础上推进指定天数。
     *
     * @param source 命令来源
     * @param days 要推进的天数，允许负数以便临时回退日期
     * @return Brigadier 命令返回值
     */
    private static int addDays(CommandSourceStack source, int days) {
        if (!CalendarConfig.enableMonths()) {
            source.sendFailure(Component.translatable("commands.xiaoj_24h_calendar.calendar.disabled"));
            return 0;
        }

        CalendarSavedData data = currentData(source);
        ServerLevel overworld = source.getServer().overworld();
        data.addDays(days, overworld);
        if (SeasonCompatManager.forceSync(overworld, data.date())) {
            data.rebindObservedDay(overworld);
        }

        CalendarDate date = data.date();
        source.sendSuccess(() -> addDaysComponent(days, date), true);

        return 1;
    }

    /**
     * 根据当前配置创建日期显示文本。
     *
     * <p>12 月制开启时使用数字月份；默认 4 月制则使用芳花月、荷风月、枫落月、霜雪月这些名称。
     *
     * @param keyPrefix 翻译键前缀，例如 {@code commands.xiaoj_24h_calendar.date}
     * @param date 要显示的日期
     * @return 本地化后的日期文本
     */
    private static Component dateComponent(String keyPrefix, CalendarDate date) {
        if (CalendarConfig.useTwelveMonths()) {
            return Component.translatable(keyPrefix + ".month_number", date.month(), date.day());
        }

        return Component.translatable(keyPrefix + ".month_name", date.monthName(), date.day());
    }

    /**
     * 创建推进日期后的命令反馈文本。
     *
     * @param days 本次推进的天数
     * @param date 推进后的日期
     * @return 本地化后的命令反馈文本
     */
    private static Component addDaysComponent(int days, CalendarDate date) {
        String keyPrefix = "commands.xiaoj_24h_calendar.adddays.success";

        if (CalendarConfig.useTwelveMonths()) {
            return Component.translatable(keyPrefix + ".month_number", days, date.month(), date.day());
        }

        return Component.translatable(keyPrefix + ".month_name", days, date.monthName(), date.day());
    }

    /**
     * 获取当前世界日历数据，并先按主世界时间同步一次。
     *
     * <p>命令可能在服务器 tick 之间被执行，先同步可以让玩家查询到更接近当前世界状态的日期。
     *
     * @param source 命令来源
     * @return 当前世界日历数据
     */
    private static CalendarSavedData currentData(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        CalendarSavedData data = CalendarSavedData.get(source.getServer());
        if (data.updateFromOverworld(overworld)) {
            if (SeasonCompatManager.syncIfNeeded(overworld, data.date())) {
                data.rebindObservedDay(overworld);
            }
        }

        return data;
    }
}
