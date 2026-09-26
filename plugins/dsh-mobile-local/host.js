/**
 * dsh-mobile-local — 宿主半（故意保持空实现）
 *
 * 这个包的全部工作都在浏览器侧（client.js）：手机端样式微调 + 任务快捷按钮。
 * 宿主半只需要存在，好让组合树里有一行、客户端模块才会被收集下发。
 * 真正的任务执行走 ~/.local/bin/dsh-tasksd（独立回环服务，白名单 + token）。
 */
export const name = 'dsh-mobile-local'

export function apply(ctx) {
  ctx.logger?.info?.('[mobileui] host ready（无宿主侧副作用）')
}
