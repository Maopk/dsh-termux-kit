#!/data/data/com.termux/files/usr/bin/bash
# 9_撤销密码授权 —— 冗余自救：一步收回「AI 可以动用你的锁屏密码」这个授权
#
# 语义：那 6 位是**系统身份验证的通用凭据**（装包要过「安全验证」、解除应用"设置限制"、
# 开发者选项里的敏感确认…都会弹它）。撤销 = 删掉 ~/.dsh-auth-pass（600 的小文件），
# 之后 AI 失去使用资格：任何需要它代过验证的动作都会停在你面前。
# **不需要看屏幕、不需要点按钮**，也不影响 DSH/桥/adb（它们各有自己的撤销入口）。
#
# 用法：9_撤销密码授权.sh [--dry-run]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
parse_args "$@"
HELPER="$HOME_DIR/.local/bin/dsh-auth-pass"
PASSFILE="$HOME_DIR/.dsh-auth-pass"

step "① 当前授权状态（只看存在与权限，不会显示密码）"
if [ "$DRY" = 1 ]; then printf '   · [dry] %s status\n' "$HELPER"; ok "（dry-run）"
elif [ -x "$HELPER" ]; then "$HELPER" status || true
else warn "找不到 $HELPER"; fi

step "② 撤销（先覆写再删除）"
if [ "$DRY" = 1 ]; then printf '   · [dry] %s revoke\n' "$HELPER"; ok "（dry-run）"
elif [ -x "$HELPER" ]; then
  if "$HELPER" revoke; then ok "撤销命令已完成"; else die "撤销失败（见上面的输出）"; fi
else die "找不到 $HELPER，无法撤销"
fi

step "③ 复核：文件必须真的没了"
if [ "$DRY" = 1 ]; then printf '   · [dry] 复核 %s 不存在\n' "$PASSFILE"; ok "（dry-run）"
elif [ -f "$PASSFILE" ]; then
  die "复核失败：$PASSFILE 还在，撤销无效"
else
  ok "已确认删除：$PASSFILE 不存在 → AI 再也拿不到这个密码去过任何验证"
fi

printf '\n   想恢复：把 6 位密码再给一次（控制台「维护」里那个开关拨回开也行）；不想给就按指纹。\n'
printf '   它只管"AI 能否动用你的锁屏密码"，桥 / adb / DSH 都不受影响（各有自己的撤销入口）。\n'
done_
