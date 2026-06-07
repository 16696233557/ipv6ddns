#!/usr/bin/env bash
# 在 Windows + gh-proxy.com 全局 gitconfig 环境下，把本项目 push 到 GitHub 直连的封装
# 用法: ./scripts/git-push-direct.sh [git push args...]
# 工作原理：临时把 ~/.gitconfig 替换为最小版（只留 credential helper + user info），
# 跑完 git 命令再恢复。这绕开了全局 insteadOf 规则。
set -e
PROJECT_NAME="ipv6ddns"
GIT_USER_NAME="16696233557"
GIT_USER_EMAIL="1308533519@qq.com"

BACKUP="$(mktemp)"
trap 'mv "$BACKUP" ~/.gitconfig 2>/dev/null || true' EXIT

if [ -f ~/.gitconfig ]; then
  mv ~/.gitconfig "$BACKUP"
fi

cat > ~/.gitconfig <<EOF
[credential "helperselector"]
	selected = manager
[user]
	name = ${GIT_USER_NAME}
	email = ${GIT_USER_EMAIL}
EOF

git "$@"
