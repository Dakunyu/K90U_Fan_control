# K90Ultra Fan Control

一个轻量 Android ROOT 风扇档位控制器，用于 Redmi K90 Ultra 风扇节点：

```sh
/sys/devices/platform/soc/soc:xiaomi_fan/target_level
```

## 功能

- 支持 `0/1/2/3/4` 五个可用档位。
- 每次切换通过 `su -c` 写入节点。
- 打开 App、回到前台、点击刷新时会读取真实当前档位。
- 中文 Compose UI，深色控制面板风格。

## 档位说明

| Level | 说明 |
| --- | --- |
| 0 | 智能 / 自动调频策略 |
| 1 | 静谧 / 低噪声档位 / 12000转 |
| 2 | 强冷 / 高速强冷档 / 15000转 |
| 3 | 增强 / 隐藏档位 / 16000转 |
| 4 | 极速 / 隐藏档位 / 20000转 |

## 构建

使用 Android Studio 打开项目，或在已配置 Android SDK / JDK / Gradle 的环境中执行：

```sh
gradle assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 注意

- 设备需要 ROOT。
- 仅适用于存在上述 `xiaomi_fan/target_level` 节点的设备。
- 本项目只做手动档位切换，不做后台常驻或自动温控策略。
## 鸣谢

本项目的风扇档位修改思路来自酷安用户 [Smartisan_Apple](https://www.coolapk.com/u/1404550)。

相关帖子：[https://www.coolapk.com/feed/72669245?s=NThmODgyNDIyMDY5OTg1ZzZhNGI3YzMyega1640](https://www.coolapk.com/feed/72669245?s=NThmODgyNDIyMDY5OTg1ZzZhNGI3YzMyega1640)
