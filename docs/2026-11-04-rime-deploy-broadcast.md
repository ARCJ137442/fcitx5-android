# Rime 重新部署广播（DEPLOY_RIME）设备实测记录

**状态：全链路验收通过（2026-11-04，Android 15 设备 + 外接键盘）**

## 背景与结论

小鹤音形词库的管理工具原先通过「复制词库文件 → `am force-stop` 输入法」触发 Rime
重新部署。实测发现 `force-stop` 会以 signal 9 波及整个进程树（提交/推送子进程失败，甚至中断
会话），因此本分支为 fcitx5-android 增加了专用广播入口 `DEPLOY_RIME`，直接触发
fcitx5-rime 的「重新部署」（与输入法 UI 的 Deploy 动作同一条路径），无需强停进程。

设备实测结论：

- 广播可用：`am broadcast -a org.fcitx.fcitx5.android.debug.action.DEPLOY_RIME`
  → `Broadcast completed: result=1`
- 部署确实执行：`user.yaml` 的 `last_build_time` 与广播时刻精确对齐
- 无需 force-stop：部署期间输入法进程未重启、输入不中断
- 候选即时生效：新增的 custom_phrase 测试条目在 logcat 与键盘上均可见

## 实测环境

- 设备：Android 15（外接键盘）
- 包名：`org.fcitx.fcitx5.android.debug`（versionName `9817e5a` / versionCode `112`；
  覆盖安装，应用数据保留）
- 发送方：shell 身份（uid 2000），满足 `android.permission.READ_INPUT_STATE` 门禁

## 实测证据（2026-11-04）

1. 两次广播均返回 `Broadcast completed: result=1`。
2. `/storage/emulated/0/Android/data/org.fcitx.fcitx5.android.debug/files/data/rime/user.yaml`
   的 `last_build_time` 两次分别为 `1793779373`、`1793779812`，与广播时刻精确相等。
3. 两次部署期间输入法进程未重启，输入不中断，无异常、无卡顿。
4. 向 `custom_phrase.txt` 追加测试短语后广播，logcat `Fcitx$JNI: Handling PagedCandidateEvent`
   出现 `text=联调测试ABC, comment=~zzz`；键盘实测同样看到该候选。
5. 移除测试短语后再次广播，候选消失（用户确认）；`custom_phrase.txt` 恢复为
   md5 `895b25845009ba847429cf6ae724db1d`、57552 B，与仓库一致。

## 全链路验收（词库保存 → 广播 → 引擎重载）

- 词库管理界面保存 → 自动提交与推送（仅涉及目标词库文件）→ `DEPLOY_RIME`
  广播 `result=1` → 状态文件 `dispatched` → 同一输入法进程重读词库文件
  并重建引擎；全程未 force-stop。
- 用户确认：键盘可直接打出新短语，最多一次重新加载，无其他异常。

## 实现契约（2026-11-04 核对）

- 接收器在 `FcitxApplication` 中**运行时注册**：`dumpsys package` 查不到，不要用
  dumpsys 探测。
- 广播 result code：`1` = 接受；`2` = 无 fcitx 实例；`0` = 无人接收。
- 状态文件：`<外部 files 目录>/rime_deploy_status.txt`，格式
  `<unix 毫秒>\t<accepted|no-instance|dispatched|failed>`。
- 生效判据：`user.yaml` 的 `last_build_time`（Unix 秒）与状态文件毫秒时间戳对齐；
  同一输入法进程内可观察到词库重读与引擎重建。

## 判据与注意事项

1. `.../files/rime_deploy_status.txt` 中的 `dispatched` 只表示广播已受理，**不能证明部署成功**；
   广播 `result=1` 同理。可靠判据是 `user.yaml` 的 `last_build_time`（Unix 秒）是否前移并与
   广播时刻对齐。
2. `flypy` 与 `custom_phrase` 在 `flypy.schema.yaml` 中均为 `db_class: stabledb`，直接读取
   `.txt`；部署后 `build/` 不会生成 `flypy.*.bin` / `custom_phrase.*.bin`（build 中只有 stroke
   的产物），**不能**用 build 目录 mtime 判断部署是否发生。
3. 候选是否更新可用 logcat `Fcitx$JNI: Handling PagedCandidateEvent(...)` 文本客观核对，
   无需仅凭肉眼。
4. 新链路不需要原先脚本末尾的 `am force-stop`；联调全程未强停。

## 用法

```sh
adb shell am broadcast -a org.fcitx.fcitx5.android.debug.action.DEPLOY_RIME
```

前置条件：输入法进程/实例存活（IME 已启用且使用过），否则广播为 no-op。

## 后续建议

- 应用侧可进一步把 `user.yaml` 的 `last_build_time` 前移作为完成判据回写状态文件，
  替代当前的 `dispatched` 语义（当前应用侧无法得知维护完成时刻）。
- 发布资产：`rime-deploy-broadcast-2026-11-04`（prerelease，APK versionName `9817e5a`）。

## 关联

- Issue：<https://github.com/fcitx5-android/fcitx5-android/issues/981>
- PR：<https://github.com/fcitx5-android/fcitx5-android/pull/982>
- Release：<https://github.com/ARCJ137442/fcitx5-android/releases/tag/rime-deploy-broadcast-2026-11-04>
