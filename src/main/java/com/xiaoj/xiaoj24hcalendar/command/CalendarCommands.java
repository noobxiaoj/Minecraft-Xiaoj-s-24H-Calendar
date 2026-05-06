package com.xiaoj.xiaoj24hcalendar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import com.xiaoj.xiaoj24hcalendar.calendar.CalendarDate;
import com.xiaoj.xiaoj24hcalendar.calendar.CalendarSavedData;

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
        dispatcher.register(Commands.literal(ROOT_COMMAND)
                .then(Commands.literal("date")
                        .executes(context -> showDate(context.getSource())))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                        .then(Commands.argument("month", IntegerArgumentType.integer(1, CalendarDate.MONTHS_PER_CYCLE))
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
                                        IntegerArgumentType.getInteger(context, "days"))))));
    }

    /**
     * 显示当前日期。
     *
     * @param source 命令来源
     * @return Brigadier 命令返回值
     */
    private static int showDate(CommandSourceStack source) {
        CalendarSavedData data = currentData(source);
        CalendarDate date = data.date();

        source.sendSuccess(() -> Component.translatable(
                "commands.xiaoj_24h_calendar.date",
                date.month(),
                date.day()), false);

        return 1;
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
        if (!CalendarDate.isValid(month, day)) {
            source.sendFailure(Component.translatable("commands.xiaoj_24h_calendar.set.invalid"));
            return 0;
        }

        CalendarSavedData data = currentData(source);
        data.setDate(new CalendarDate(month, day), source.getServer().overworld());

        source.sendSuccess(() -> Component.translatable(
                "commands.xiaoj_24h_calendar.set.success",
                month,
                day), true);

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
        CalendarSavedData data = currentData(source);
        ServerLevel overworld = source.getServer().overworld();
        data.addDays(days, overworld);

        CalendarDate date = data.date();
        source.sendSuccess(() -> Component.translatable(
                "commands.xiaoj_24h_calendar.adddays.success",
                days,
                date.month(),
                date.day()), true);

        return 1;
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
        data.updateFromOverworld(overworld);

        return data;
    }
}
