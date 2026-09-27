/**
 * dsh-mobile-local — host half (deliberately kept empty)
 *
 * All of this package's work happens on the browser side (client.js): mobile style tweaks + task shortcut buttons.
 * The host half only needs to exist so the composition tree has a row, so the client module gets collected and delivered.
 * Real task execution goes through ~/.local/bin/dsh-tasksd (a standalone loopback service, allowlist + token).
 */
export const name = 'dsh-mobile-local'

export function apply(ctx) {
  ctx.logger?.info?.('[mobileui] host ready (no host-side side effects)')
}
