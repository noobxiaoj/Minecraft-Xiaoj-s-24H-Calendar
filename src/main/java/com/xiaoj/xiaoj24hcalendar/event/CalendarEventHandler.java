package com.xiaoj.xiaoj24hcalendar.event;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.xiaoj.xiaoj24hcalendar.calendar.CalendarSavedData;
import com.xiaoj.xiaoj24hcalendar.command.CalendarCommands;
import com.xiaoj.xiaoj24hcalendar.compat.SeasonCompatManager;

/**
 * NeoForge 事件监听入口。
 *
 * <p>事件处理代码集中在这里，主类只负责注册监听器。这样后续添加 BetterDays
 * 兼容层、日历道具事件、节日广播时，可以继续按功能拆分，不让模组入口膨胀。
 */
public final class CalendarEventHandler {

    private CalendarEventHandler() {
    }

    /**
     * 注册服务端命令。
     *
     * @param event NeoForge 命令注册事件
     */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CalendarCommands.register(event.getDispatcher());
    }

    /**
     * 在每个服务端 tick 结束后推进日历。
     *
     * <p>选择 Post 阶段是为了尽量读取已经由原版或其他时间控制模组更新后的主世界时间。
     * 后续接入 BetterDays 时，这里仍然只负责日历推进，时间速度覆盖会放到独立兼容层。
     *
     * @param event 服务端 tick 结束事件
     */
    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        CalendarSavedData data = CalendarSavedData.get(server);
        data.updateFromOverworld(server.overworld());

        if (SeasonCompatManager.syncIfNeeded(server.overworld(), data.date())) {
            data.rebindObservedDay(server.overworld());
        }
    }
}
