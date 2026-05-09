# Xiaoj's 24H Calendar

Xiaoj's 24H Calendar 是一个面向 Minecraft NeoForge 的日历模组。

当前开发分支目标：

- Minecraft `1.21.1`
- NeoForge `21.1.x`
- Loader 分支：`1.21.1-neoforge`

## 当前功能

- 无年份虚拟日期系统
- 默认一年 4 个月，每个月 30 天：芳花月、荷风月、枫落月、霜雪月
- 可通过配置切换为 12 个月，每个月 30 天，月份使用数字显示
- 日期随主世界天数推进
- 命令查询和调整日期
- Ecliptic Seasons 可选联动：一个循环平均同步为 24 个节气
- Serene Seasons 可选联动：一个循环平均同步为 12 个 Early / Mid / Late 小季节
- Fabric Seasons / Forge Seasons 可选联动：一个循环平均同步为 4 个大季节，没有 Early / Mid / Late 小季节
- 如果同时安装多个受支持季节模组，季节联动会直接失效

## 配置

NeoForge 会生成服务端配置，包含：

- `calendar.enableMonths`：是否启用本模组的月份/日期功能，关闭后日期不会推进，日期命令不可用，只保留后续 24H 时间功能
- `calendar.enableSeasons`：是否由本模组同步受支持季节模组；关闭后不联动、不干预，季节模组按自己的配置运行
- `calendar.useTwelveMonths`：是否启用 12 月制；开启后一年 12 个月，每月 30 天，月份使用数字显示

## 命令

```text
/xcalendar date
/xcalendar set <month> <day>
/xcalendar adddays <days>
```

`/xcalendar season` 只会在月份功能开启、季节联动开启，并且刚好安装 1 个受支持季节模组时注册。

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
