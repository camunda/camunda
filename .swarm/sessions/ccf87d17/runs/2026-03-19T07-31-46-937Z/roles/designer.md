# designer Summary

**Timestamp:** 2026-03-19T07:50:13.368Z

---

## Clarification answer: §5.5 Base64 input mode

**Confirmed:** Base64 was intended as a fallback, not a peer. But heuristic detection is unreliable — base64-encoded JSON would be silently double-encoded. **Resolution:** replaced with an explicit `ContentSwitcher` toggle ("JSON (auto-encoded)" / "Raw Base64"). Users choose their encoding path before typing; no auto-detection occurs. Switching modes clears the field after a confirmation `Modal`.

---

## Changes in v3

**Usability**
- **Touch interaction** (§5.3): Hover-only tooltips replaced with overlay `<button>` per tick mark — triggers on hover, `:focus-visible`, and **single tap** (`touchend`); no long-press required. Touch model applied under `pointer: coarse` detection
- **Warning Tag tooltip content** (§5.4): Full tooltip text specified, including the sentence "If you do not have log access, contact your cluster administrator" for users without log access
- **>50 consumer grouping key** (§5.3): Grouping key is `groupId`; the spec confirms one group per consumer; no multi-group membership case exists; aggregate tick shows `min(committedPosition)` per group

**Accessibility**
- **SVG tick keyboard focus — WCAG 1.4.13** (§5.3): Overlay `<button>` elements on each tick carry `aria-label` and `aria-describedby`; tooltip fires on focus, hover, and tap
- **Compact table structure** (§5.3): Full HTML with `<caption class="sr-only">`, `th scope="col"` on headers, and `aria-label` on `<table>`
- **Rebalance warning Tag icon** (§5.4): `WarningFilled` 16 px icon added — state conveyed by icon shape + word "Warning", not color alone (WCAG 1.4.1)
- **JSON validation live region** (§5.5): Helper text `<p>` carries `role="status"` `aria-live="polite"` `aria-atomic="true"`; transitions between all four states are announced
- **SideNav icon rail** (§2.1): Explicit requirement to use Carbon `SideNavIcon` variant; bare icons prohibited
- **Global sticky banner** (§3): Banner slot `<div>` carries `role="alert"` `aria-live="assertive"` `aria-atomic="true"`
- **Focus restoration** (§5.3): `tabIndex="0"` on navigable `<tr>` elements; `sessionStorage` key scoped by route (`eb.focus.partitions`), keyed by partition ID (not DOM offset); stale-key fallback to `<h1>` focus

**Design System**
- **`$highlight` replaced** (§9): Defined as `--eb-row-flash-bg: #bee6ff` (custom CSS property with inline documentation); `$highlight` removed throughout

**Edge Cases**
- **Partial API failures** (§15 new section): Show available data; scoped `InlineNotification` per failed sub-request; retry re-fetches only the failed portion; full-page error only on first-load total failure
- **Empty vs. zero-result** (§7.1 new section): Empty-state `Tile` component only for "no data exists"; zero-result uses inline toolbar message + "Clear filter/search" ghost `Button` — different copy, no `Tile`, no icon
- **Rebalance polling cadence** (§5.4): 5 s poll cadence; generation considered stable only after **two consecutive identical readings** (10 s window) to prevent premature spinner clear
