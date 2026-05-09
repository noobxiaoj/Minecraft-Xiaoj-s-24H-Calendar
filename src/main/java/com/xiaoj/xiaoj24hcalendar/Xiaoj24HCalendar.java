package com.xiaoj.xiaoj24hcalendar;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

import com.xiaoj.xiaoj24hcalendar.config.CalendarConfig;
import com.xiaoj.xiaoj24hcalendar.event.CalendarEventHandler;

/**
 * Xiaoj's 24H Calendar 的 NeoForge 入口类。
 *
 * <p>这个类只负责把模组接入 NeoForge 事件系统，具体业务逻辑会拆到
 * {@code calendar}、{@code command} 和 {@code event} 包中，方便后续继续添加
 * BetterDays 联动、节日 API 和日历道具时保持主入口轻量。
 */
@Mod(Xiaoj24HCalendar.MOD_ID)
public class Xiaoj24HCalendar {

    /** 模组 ID，必须和 {@code neoforge.mods.toml} 中的 modId 保持一致。 */
    public static final String MOD_ID = "xiaoj_24h_calendar";

    /** 统一日志对象，便于后续排查服务端日期推进和兼容层问题。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 创建模组实例并注册通用事件。
     *
     * @param modEventBus NeoForge 提供的模组生命周期事件总线，当前阶段暂不需要使用。
     * @param modContainer 当前模组容器，保留参数用于符合现代 NeoForge 构造器风格。
     */
    public Xiaoj24HCalendar(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, CalendarConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(CalendarEventHandler::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(CalendarEventHandler::onServerTickPost);
    }
}
