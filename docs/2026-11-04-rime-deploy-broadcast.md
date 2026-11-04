# Rime 重新部署广播（DEPLOY_RIME）设备实测记录

**状态：设备实测通过（2026-11-04，Android 15 平板）**

## 背景与结论

小鹤音形词库的 Termux 管理工具原先通过「复制词库文件 → `am force-stop` 输入法」触发 Rime
重新部署。实测发现 `force-stop` 会以 signal 9 波及 Termux 进程树（git 子进程失败，甚至杀死
调查会话），因此本分支为 fcitx5-android 增加了专用广播入口 `DEPLOY_RIME`，直接触发
fcitx5-rime 的「重新部署」（与输入法 UI 的 Deploy 动作同一条路径），无需强停进程。

设备实测结论：

- 广播可用：`am broadcast -a org.fcitx.fcitx5.android.debug.action.DEPLOY_RIME`
  → `Broadcast completed: result=1`
- 部署确实执行：`user.yaml` 的 `last_build_time` 与广播时刻精确对齐
- 无需 force-stop：部署期间输入法进程 PID 不变、输入不中断
- 候选即时生效：新增的 custom_phrase 测试条目在 logcat 与键盘上均可见

## 实测环境

- 设备：Android 15 平板
- 包名：`org.fcitx.fcitx5.android.debug`（versionName `9817e5a` / versionCode `112`）
- 发送方：rish（Shizuku shell uid 2000），满足 `android.permission.READ_INPUT_STATE` 门禁

## 实测证据（2026-11-04）

1. 两次广播均返回 `Broadcast completed: result=1`。
2. `/storage/emulated/0/Android/data/org.fcitx.fcitx5.android.debug/files/data/rime/user.yaml`
   的 `last_build_time` 两次分别为 `1793779373`、`1793779812`，与广播时刻精确相等。
3. 两次部署期间输入法进程 PID `17319` 未变，输入不中断，无异常、无卡顿。
4. 向 `custom_phrase.txt` 追加测试短语后广播，logcat `Fcitx$JNI: Handling PagedCandidateEvent`
   出现 `text=联调测试ABC, comment=~zzz`；键盘实测同样看到该候选。
5. 移除测试短语后再次广播，候选消失（用户确认）；`custom_phrase.txt` 恢复为
   md5 `895b25845009ba847429cf6ae724db1d`、57552 B，与仓库一致。

## 判据与注意事项

1. `.../files/rime_deploy_status.txt` 中的 `dispatched` 只表示广播已受理，**不能证明部署成功**；
   广播 `result=1` 同理。可靠判据是 `user.yaml` 的 `last_build_time`（Unix 秒）是否前移并与
   广播时刻对齐。
2. `flypy` 与 `custom_phrase` 在 `flypy.schema.yaml` 中均为 `db_class: stabledb`，直接读取
   `.txt`；部署后 `build/` 不会生成 `flypy.*.bin` / `custom_phrase.*.bin`（build 中只有 stroke
   的产物），**不能**用 build 目录 mtime 判断部署是否发生。
3. 候选是否更新可用 logcat `Fcitx$JNI: Handling PagedCandidateEvent(...)` 文本客观核对，
   无需仅凭肉眼。
4. 新链路不需要 `rish-rime-mv.sh` 末尾的 `am force-stop`；联调全程未强停。

## 用法

```sh
# rish（shell uid 2000）
rish -c "am broadcast -a org.fcitx.fcitx5.android.debug.action.DEPLOY_RIME"

# 或 adb
adb shell am broadcast -a org.fcitx.fcitx5.android.debug.action.DEPLOY_RIME
```

前置条件：输入法进程/实例存活（IME 已启用且使用过），否则广播为 no-op。

## 后续建议

- 应用侧可进一步把 `user.yaml` 的 `last_build_time` 前移作为完成判据回写状态文件，
  替代当前的 `dispatched` 语义（当前应用侧无法得知维护完成时刻）。
- 发布资产：`rime-deploy-broadcast-2026-11-04`（prerelease，APK versionName `9817e5a`）。
