# 剪贴板键盘操作第二阶段

**状态：主体键盘操作已设备验证；剪贴板历史搜索面板待设备实测。**

**构建：** 2026-11-03，arm64 Debug APK；包名未变。

本阶段新增：

- 最近历史中用可配置的 D 置顶当前候选、用可配置的 X 删除单条原生历史。
- F 打开独立收藏页；收藏页支持数字/空格粘贴、X 取消置顶、Q 返回、`[` / `]` 翻页及 Ctrl+↑/↓调整顺序。
- 收藏固定顺序默认关闭。动态模式下第一次 Ctrl+↑/↓会启用固定顺序并立即调整。
- 输入法设置页可配置 D/X 和收藏排序；剪贴板插件设置页可配置触发键及原生历史数量。
- Room 数据库从版本 4 自动迁移到版本 5，新增收藏顺序字段。

## 2026-11-03 回归修复

- 设备崩溃栈定位到两个 JNI 方法在返回对象数组前由 `JRef` 提前释放了局部引用，触发 ART `CheckReferenceResult` 的 `SIGSEGV`。
- 原生剪贴板中的 D/X 继续保留原行为，并在 JNI 返回边界修复该生命周期错误。
- 原生剪贴板菜单新增 Q：截获按键后向 Fcitx 发送 Escape，关闭临时剪贴板模式。
- 设备人工实测确认修复后 D/X/F/Q 均正常，不再触发闪退。
- 根据本轮验证反馈，不再以统一的 Room 剪贴板菜单替换现有“剪贴板”和“置顶收藏”两套界面。
- 附加组件设置新增 F/D/X/Q、清空历史、上一页和下一页快捷键；置顶页与原生提示均读取同一配置。
- 原生清空历史不再硬编码退格/删除，默认值仍为退格与删除，并随配置显示实际按键。

## 2026-11-03 触发入口拆分

- “唤起剪贴板”默认仅为 Ctrl+;，“唤起置顶菜单”独立默认为 Alt+Shift+V。
- 启动时会把旧 `TriggerKey` 中与置顶菜单触发键重复的项目迁出并保存。
- 新增“显示快捷键提示”开关，默认开启；关闭后隐藏原生提示后缀与置顶页提示行。
- 数字选择、粘贴选中项、上下选择、置顶项上下移动、原版 Escape 关闭也已加入附加组件配置。
- 置顶收藏的快捷键提示移到标题下方，并按面板高度预留列表空间，避免提示行被挤出输入法窗口。

## 设备实测清单

- [ ] 从上一版覆盖安装，确认 Room 历史和收藏在 4→5 迁移后仍保留。
- [ ] 打开 Ctrl+; 最近历史，确认提示行显示退格/删除清空、D 置顶、X 单删、F 收藏。
- [ ] 选中最近项按 D；打开收藏确认内容与敏感标记正常。
- [ ] 在最近历史按 X，确认只移除当前原生历史项；退格/删除仍清空全部原生历史。
- [ ] 在最近历史按 Q，确认原生剪贴板候选立即关闭且输入法进程不退出。
- [ ] 在附加组件/剪贴板设置中修改 F/D/X/Q 或清空历史键，确认菜单提示和实际动作同步更新。
- [x] 确认“显示快捷键提示”开关可在设备上保存为 True/False，并恢复为默认 True。
- [ ] 确认置顶收藏页在收藏项较多时仍显示快捷键提示行。
- [ ] 在收藏页验证 1–9、空格粘贴，X 取消置顶，Q 返回，以及 `[` / `]` 翻页。
- [ ] 在动态排序下用 Ctrl+↑/↓，确认提示一次后切到固定顺序、选中项保持可见且顺序保存。
- [ ] 覆盖九项以上收藏，检查跨页调整和取消置顶后的焦点位置。
- [ ] 在应用设置中修改 D/X 与插件 TriggerKey，确认新按键立即生效且冲突组合被拒绝。
- [ ] 按设备实际系统行为检查 Esc/Back；它们不作为收藏页内部返回键。

## 构建产物

- `app/build/outputs/apk/debug/org.fcitx.fcitx5.android-e72366d-arm64-v8a-debug.apk`
- `plugin/rime/build/outputs/apk/debug/org.fcitx.fcitx5.android.plugin.rime-a4a34b3-arm64-v8a-debug.apk`

构建已成功；上述设备交互清单尚待人类在设备上验证。

## 2026-11-04 空键列表修复验收

`bb5c58e`（cherry-pick 到 `codex/rime-deploy-broadcast` 为 `b08ec30`）在 Android 15 设备（外接键盘）上通过实测：

- `pm install -r` 覆盖安装（versionCode 112，数据保留；输入法进程未重启，未 force-stop）。
- `clipboard.conf` 中 `FavoritesTriggerKey=` 确认为空值，用户此前的「取消」确已保存。
- `Alt+Shift+V` 不再被拦截，按键放行给远端应用；改回 `Alt+Shift+B` 恢复置顶页；重启输入法后行为保持。
- `Ctrl+;` 唤起、数字选择等原有剪贴板功能未见回归。
- Release：`clipboard-empty-trigger-2026-11-04`（标题已改标「设备实测通过」，资产 SHA-256 `7ABAB671…5C0CA`）。

## 2026-11-05 剪贴板历史搜索（待实测）

- 分支/提交：`codex/rime-deploy-broadcast` `ab28f8f`，核心子模块 `codex/clipboard-history-actions` `5ddb9ab`。
- 新增 `ClipboardHistoryOverlay`：直接输入字符即搜（Room 历史库，80ms 防抖）、`Backspace`/`Delete` 退格、`↑↓` 选择、`Enter` 粘贴、`Esc` 关闭、`[`/`]` 与 `PageUp`/`PageDown` 翻页（每页 10 项）。
- 入口：全局 `Alt+Shift+F`（核心配置 `SearchTriggerKey`）；原生 `Ctrl+;` 面板内按 `S`（`SearchFilterKey`）。
- 面板内动作：`Ctrl+P`/`Alt+P` 置顶切换、`Alt+X` 删除选中项。
- 核心插件新增 `CandidatePageSize`（每页候选数，默认 10），原生 `Ctrl+;` 面板按该值分页。
- 修正 `SelectionKeys`：默认 `1..9,0` 沿用数字位置匹配；自定义顺序/删项按实际键列表匹配；空列表不回退默认。
- Release：`clipboard-history-search-2026-11-05`（prerelease，资产 `fcitx5-android-clipboard-history-search-arm64-v8a-debug.apk`，SHA-256 `91CDC23F…1AD19A`，versionCode 112，versionName 显示 `b08ec30`）。
- 已知限制：仅文本剪贴项；需开启「记录剪贴板历史」并调整历史上限；搜索面板内 `[`/`]` 固定用于翻页。

## 2026-11-05 搜索交互补丁（待实测）

- 分支/提交：`codex/rime-deploy-broadcast` `bde10c3`，核心子模块 `codex/clipboard-history-actions` `7d83976`。
- 空输入返回：搜索框为空时按 Backspace/Delete 关闭面板；有输入时先逐字删除。
- 键位语义：空格只作为搜索字符，不触发粘贴；仅 Enter / 小键盘 Enter 上屏；Tab / Shift+Tab 在列表中前后循环（首尾环绕）。
- `Ctrl/Alt+1..9/0`：直接粘贴列表第 1..10 项。
- 新增核心配置 `SearchTriggerKeySecondary`（“Additional clipboard history search trigger keys”），默认空列表；与现有 `SearchTriggerKey`（默认 Alt+Shift+F）并存。
- Release：`clipboard-search-ux-2026-11-05`（prerelease，资产 `fcitx5-android-clipboard-search-ux-arm64-v8a-debug.apk`，SHA-256 `8F3CA261…AF752`，versionName `bde10c3`，versionCode 112）。
- 未包含：搜索框接收软键盘/IME 输入。当前面板覆盖输入法窗口且只处理硬件 `KeyEvent`；要支持需同时改「面板不遮挡键盘」与「软键盘 KeyAction 路由」，拼音上屏还需拦截 commit 事件，先确认语义（原始按键 vs 完整 IME 上屏）。

## 2026-11-05 搜索框接收输入法上屏（待实测）

- 分支/提交：`codex/rime-deploy-broadcast` `85aaee4`。
- 面板改为只占「键盘上方区域」（`FrameLayout` 底部 margin = 软键盘高度），软键盘保持可见可点；面板在可用区域内居中，并在布局后按实际标题/提示高度收缩列表，避免溢出到键盘区域。
- 触摸：面板打开期间 `onComputeInsets` 使用 `TOUCHABLE_INSETS_REGION` 把整个输入法窗口设为可触控。此前键盘上方区域默认不触控，面板/候选项点不到；现在点列表项即粘贴、点外侧即关闭，`favorites` 置顶面板同样受益。
- 候选项上屏：虚拟键盘模式显示键盘顶栏（KawaiiBar）候选条；物理键盘模式使用浮动候选窗，并 `bringToFront()` 保证候选窗盖在面板之上可点击。点击候选走 `fcitx.select()`，其提交被重定向到搜索词。
- 输入路由（方案 B）：
  - `CommitStringEvent` / `commitText()` 在面板打开时改为写入搜索查询（`appendHistoryInput`），不再送进目标编辑器。
  - `ClientPreeditEvent` 不再调用 `updateComposingText`（不碰编辑器），改为在搜索框内显示组合串；`InputPanelEvent` 的 preedit 作为未启用客户端 preedit 时的回退。
  - `DeleteSurroundingEvent` 映射为删除查询字符；空查询时按退格直接关闭面板。
  - 硬件键在面板打开时转发给 fcitx 引擎（拼音可组合、可选候选）；组合中把方向键/回车/退格/数字等交给引擎，未组合时面板处理导航与快捷键（Tab、↑↓、Ctrl/Alt+数字、Ctrl+P、Alt+X、`[`/`]`、PageUp/PageDown、Esc）。
  - 打开/关闭面板都会 `reset()` 引擎组合态并清空 preedit，避免组合串泄漏到编辑器。
- Release：`clipboard-search-ime-2026-11-05`（prerelease，资产 `fcitx5-android-clipboard-search-ime-arm64-v8a-debug.apk`，SHA-256 `153316D2…CEF3A`，versionCode 112，versionName `85aaee4`）。
- 已知限制：面板打开时目标应用不可点击（模态面板）；物理键盘模式下浮动候选窗位置仍跟随编辑器光标；搜索仍只覆盖文本剪贴项。

## 2026-11-05 设备实测通过（85aaee4）

- 构建：`85aaee4`（versionName `85aaee4`，versionCode `112`）。
- 安装：`pm install -r` 被 HyperOS 拒绝（`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，`--user 0` 无效，屏幕已唤醒）；中转副本与设备 `base.apk` MD5 均为 `1b36209318edb1c5141adbf21961f771`，与 Release 资产一致，排除包问题。最终由用户用 MT 管理器手动覆盖安装成功。
- 装后核验：`versionName=85aaee4`、`versionCode=112`；数据保留（`clipboard.conf` 中 `FavoritesTriggerKey=` 仍为空，`CandidatePageSize` / `SearchTriggerKey` / `SelectionKeys` 均为注释默认值）；输入法进程 pid 16880；`logcat` 4000 行内无 `FATAL EXCEPTION` / ANR。
- 用户验收（原话「功能验证基本完成」）：搜索框可直接用输入法输入文字搜索（方案 B 生效）、退格正常、触屏正常。
- 仍欠：H3 `DEPLOY_RIME` 广播回归（本次未跑；脚本在 `result=0` 时会退回 `am force-stop`，待择机单独验证后回填）。本次改动不涉及部署/广播路径。
- 设备侧限制（已解除）：该平板一度拒绝 shell/USB 安装 APK（`INSTALL_FAILED_USER_RESTRICTED`）；用户重启设备后 USB 安装恢复正常，后续联调可继续用 `adb` 安装，MT 管理器仅作备用。
