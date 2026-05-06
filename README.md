# Xiaoj's 24H Calendar

Xiaoj's 24H Calendar 是一个面向 Minecraft NeoForge 的日历模组。

当前开发分支目标：

- Minecraft `1.21.1`
- NeoForge `21.1.x`
- Loader 分支：`1.21.1-neoforge`

## 当前功能

- 无年份虚拟日期系统
- 一年 4 个月，每个月 30 天
- 日期随主世界天数推进
- 命令查询和调整日期

## 命令

```text
/xcalendar date
/xcalendar set <month> <day>
/xcalendar adddays <days>
```

## 后续计划

- BetterDays 可选联动
- 自定义一天时长和睡眠加速倍率
- 节日 API
- 日历道具
- 季节模组联动
- 植物生长节奏控制

## 构建

```bash
./gradlew build
```

## 许可证

本项目当前采用 `MIT` 许可证。
你可以自由使用、修改、分发和再发布本项目，包括商业使用。

如果你在二次开发、发布页或致谢中顺手提及原项目和作者 `noobxiaoj`，
我会很感谢；不过这不是强制要求，也不是许可证附加限制。

完整条款见 [LICENSE.md](LICENSE.md)。
