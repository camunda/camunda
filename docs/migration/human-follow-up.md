## Scan: 2026-07-21 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/PriorityLabel.tsx

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/PriorityLabel.module.scss`
- **Tokens found:** `--cds-text-primary` (`.popoverBody`), `--cds-spacing-01` (`.inlineIcon`)
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`.
- **Suggested approach:** replace `var(--cds-text-primary)` with `var(--foreground)` and `var(--cds-spacing-01)` with the DS spacing scale equivalent when this SCSS module is next touched.
- **Shim applied:** No (out of scope, not touched)

## Deferred — HOT file (2026-07-22)

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/custom-filters/AdvancedStringFilter.tsx` — modified 16 days ago (2026-07-06, "refactor: move operator config out of component in OC webapp"; 2026-07-02 feature commit adding it). Inside the 30-day HOT window — deferred per the migration agent's own safety gate. Revisit after 2026-08-05 (30 days from last touch), or sooner if coordinated with whoever owns that feature work.
  - Findings recorded for when it's revisited: `TextInput` is a clean SWAP. `Dropdown` is classified REMAP in `mapping.json` but the `carbon-compat/dropdown.tsx` adapter is actually prop-compatible (same pattern as `OverflowMenu` — no JSX restructuring needed). One real risk to check when migrating: the adapter silently drops `size` and `direction` props; this file passes `direction="top"`, which the DS's Radix-backed Select doesn't support — the dropdown may open downward instead of upward, risking viewport clipping depending on where the filter renders. Needs a manual visual check once unblocked.
- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/process-diagram/ProcessDiagramView.tsx` — modified 19 days ago (2026-07-02, "feat: migrate task details process tab"). Pre-flight analysis mis-bucketed this as a safe bulk-scope target (score 0.75, SWAP+SHIM only); independent git-log check inside this agent run found it inside the 30-day HOT window — deferred per the migration agent's own safety gate, not touched. The sibling `DiagramControls.tsx` in the same directory shares the identical last-touch commit/date and is HOT for the same reason (confirms the tip that same-feature-area siblings move together). Revisit after 2026-08-01 (30 days from last touch), or sooner if coordinated with whoever owns the process-diagram feature work.
  - Findings recorded for when it's revisited: both symbols are low-risk. `Tag` is SWAP — `carbon-compat/tag.d.ts` reuses Carbon's own `TagProps` type, fully prop-compatible, no JSX changes needed. `Layer` is SHIM — `carbon-compat/layer.d.ts` re-exports Carbon's `Layer` unchanged (paper-move only, not yet shadcn-native); swap the import path, no prop changes. `src/shared/design-system-compat/index.ts` does not yet export either symbol — both need new named exports added there (Tag from `@carbon/react` + `@camunda/design-system/carbon-compat`, feature-flagged on `featureFlags.dsTasklistUI`; Layer likewise, sourced from `@camunda/design-system/carbon-compat`) following the existing pattern before this file's imports can be repointed to `#/shared/design-system-compat`.

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/AsyncActionButton/AsyncActionButton.tsx` — modified 2026-06-30 ("fix: add clearTimeout guard"), preceded by 2026-06-29 ("feat: migrate tasklist task assignment"); sibling test touched 2026-07-01. Inside the 30-day HOT window (in-flight task-assignment feature work). Deferred — revisit after ~2026-07-30, or coordinate with the feature owner.

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details-history/components/HistoryItemDetailsModal.tsx` — modified 2026-07-08 ("refactor: fix history details modal structure", real content change by Vinicius Goulart). 14 days old, inside HOT window. Deferred. Analysis done ahead of time: ComposedModal/ModalHeader/ModalBody already have compat exports (prop-compatible, no restructuring needed); StructuredList* would need new exports; icons EventSchedule→CalendarClock, UserAvatar→User (lucide fallback, no registry match). Ready to run once cooled or cleared by the developer.

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/TurnOnNotificationPermission.tsx` — modified 2026-06-25 ("feat: migrate task details layout to unified webapp"), 27 days old, inside HOT window. Deferred.
  **Separately, a real blocker regardless of HOT status:** `carbon-compat/actionable-notification.tsx` in the DS package is not actually implemented yet — it's a bare passthrough re-export of `@carbon/react`'s `ActionableNotification`, explicitly marked `// MIGRATION TODO: adapter pending Alert shadcn component`. `mapping.json`/`carbon-migration-tiers.md` both call this REMAP-ready, but the shipped adapter doesn't back that up. This one can't be meaningfully migrated at all until the DS team ships the real adapter — worth flagging to them directly, separate from this app-repo migration.

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/process-diagram/DiagramControls.tsx` — created 2026-07-02 ("feat: migrate task details process tab", Vinicius Goulart), 20 days old, inside HOT window. Deferred. Analysis done ahead: `IconButton` is a genuine REMAP needing JSX restructuring (Button+Tooltip composition, not a drop-in) — will need a small wrapper component in design-system-compat, not the simple ternary pattern. Icons: Add→ZoomIn, Subtract→ZoomOut, CenterCircle→Crosshair (all lucide fallback, no registry match).

## Deferred — entire opus tier, all HOT (2026-07-22, checked directly, not dispatched)

Checked git history for all 9 opus-tier files before dispatching any migration agent — every one falls inside the 30-day HOT window, confirming this is the same broad in-flight "migrate to unified webapp" effort seen across the sonnet tier. Skipped dispatching agents for these (would have just re-confirmed HOT status at token cost). Revisit each individually once its last-touch date clears 30 days, or once coordinated with the feature owner.

- `modules/available-tasks/components/custom-filters/FieldsModal.tsx` — 2026-07-02, "feat: add AdvandedStringFilter to OC Tasklist for Business ID". Also has icon imports (`Close`, `Add`) not counted by the analyzer.
- `modules/available-tasks/components/CollapsiblePanel.tsx` — 2026-06-19, "feat: migrate custom filters no unified webapp". Icons: `Filter`, `SidePanelClose`, `SidePanelOpen`. Also has a FLAG-tier symbol (`ButtonSet`) per the bulk scope.
- `modules/task-details-history/components/HistoryTable.tsx` — 2026-07-08 (most recent), "refactor: turn history details button into link". Icon: `Information`. Has a FLAG-tier symbol (`DataTableHeader`) and penalty symbols (`DataTable`, `TableContainer`) per the bulk scope.
- `modules/task-details/components/DetailsSkeleton.tsx` — 2026-06-25, "feat: migrate task details layout to unified webapp". Has a FLAG-tier symbol (`ButtonSkeleton`) per the bulk scope.
- `modules/task-details/components/Aside.tsx` — 2026-07-01, "feat: add Business ID in OC Tasklist".
- `modules/task-details/components/TaskDetailsLayout.tsx` — 2026-07-06, "test: add task details history modal tests".
- `modules/task-details/useTaskCompletion.ts` — 2026-07-03, "refactor: enhance state machine state". Bulk scope flagged this file's only import (`useQueryClient`) as FLAG-tier, which is unusual — worth double-checking that's a real Carbon/DS-relevant symbol and not an analyzer misfire once revisited.
- `pages/NoTaskSelectedPage.tsx` — 2026-06-19, "feat: migrate custom filters no unified webapp".
- `pages/TaskDetailsHistoryPage.tsx` — 2026-07-06, "refactor: generalize route params".

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/pages/TaskDetailsProcessError.tsx` — modified 2026-07-06 ("refactor: simplify error pages"), preceded by 2026-07-02 ("feat: migrate task details process tab"). 16 days old, inside HOT window, same active developer/window as other deferrals. Deferred. Button/InlineNotification already have compat exports ready for when this clears.

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/TaskDetailsHeader.tsx` — modified 2026-06-25 ("feat: migrate task details layout to unified webapp"), 27 days old, inside HOT window. Deferred. Icon mapping confirmed for later: CheckmarkFilled → CircleCheck (documented in carbon-icons-to-lucide.md, not a guess).

## Deferred — HOT file (2026-07-22), continued

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/Task.tsx` — last committed 2026-07-08 (`8f10dff4555` "fix: preserve filters on task navigation", preceded same day by `011420bfdc7` "refactor: simplify task component"; also touched 2026-07-01 "feat: add Business ID in OC Tasklist" and 2026-06-25 "feat: migrate task details layout to unified webapp"). 14 days old — inside the 30-day HOT window. Deferred per the migration agent's own safety gate. This is a core, high-visibility file (renders every task row in the list), so the git-log recency check was run explicitly rather than trusting the pre-flight import-count score (which only saw `Stack` and scored 1.0 — it does not account for recency or for the four `@carbon/react/icons` imports). No edits were made to this file or to `src/shared/design-system-compat/index.ts` on its behalf. Revisit after 2026-08-07 (30 days from last touch), or sooner if coordinated with whoever owns the recent task-navigation/filter work.
  - Findings recorded for when it's revisited: `Stack` is SWAP-tier and already has a ready-made feature-flagged export in `src/shared/design-system-compat/index.ts` (`CompatStack`/`CarbonStack`) — no new export needed for that one, just repoint the import.
  - Icon findings (none exported from `design-system-compat/index.ts` yet — all four need new named exports added there following the existing `CarbonX`/`LucideX`/ternary pattern):
    - `Calendar` (creation-date icon, no color prop) → Lucide `Calendar` — identical name, trivial REMAP, generic UI concept (not a domain entity, no registry match).
    - `CheckmarkFilled` (completion-date icon, `color="green"`) → Lucide `CircleCheck` — documented in `carbon-icons-to-lucide.md`'s "Common remaps" table. Carbon's icon is filled by name; pair with `className="fill-current"` plus a semantic color utility (`text-success-foreground-strong`) instead of carrying over the raw `color="green"` string, per DS semantic-token rules (AGENTS.md rule 2) and the KB's "filled variant" guidance.
    - `Warning` (overdue-date icon, `color="red"`) → Lucide `TriangleAlert` — not a literal row in `carbon-icons-to-lucide.md` (only `WarningAlt`, `WarningAltFilled`, `WarningFilled`, `Caution` are listed, all → `TriangleAlert`); treating bare `Warning` as the same family is consistent with the existing `CriticalIcon` precedent already in this same `design-system-compat/index.ts` file (`Critical` → `TriangleAlert`, commented as a family/closest-match call, not a literal table row). Pair with `fill-current` + `text-danger-foreground-strong` in place of `color="red"`.
    - `Notification` (follow-up-date icon, `color="blue"`) → no table entry at all — genuine gap. Closest Lucide equivalent is `Bell` (fallback tier per `carbon-icons-to-lucide.md`'s "No-equivalent → placeholder" rule, since a real closest-match exists this is a documented fallback rather than `CircleDashed`). Pair with `fill-current` + `text-info-foreground-strong` in place of `color="blue"`. Log this as an icon gap when applied.
  - No FLAG-tier symbols found in this file.

- `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/pages/TaskDetailsHistoryErrorPage.tsx` — modified 2026-07-06 ("test: add task details history tab tests"), preceded by 2026-07-03 feature commit. 16 days old, inside HOT window, same active window as the rest of task-details-history. Deferred. Icon mapping confirmed for later: Launch → ExternalLink (documented in carbon-icons-to-lucide.md).

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/process-diagram/ProcessDiagramView.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete). Migrated: `Tag`, `Layer` (both SWAP, reused existing compat exports, no JSX changes). No FLAG-tier symbols in this file.

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/process-diagram/ProcessDiagramView.module.scss`
- **Tokens found:** `--cds-spacing-03`, `--cds-spacing-05` (x2), `--cds-text-primary`, `--cds-layer`
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`. No inline `style=` tokens found in the `.tsx` file itself.
- **Suggested approach:** resolve to DS tokens/Tailwind when this SCSS module is next touched (`--cds-spacing-*` → Tailwind spacing scale, `--cds-text-primary` → `text-foreground`/`var(--foreground)`, `--cds-layer` → `bg-card`/`var(--card)` per the `--cds-layer-01` row).
- **Shim applied:** No (out of scope, not touched)

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details-history/components/HistoryItemDetailsModal.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete). Migrated: `ComposedModal`/`ModalHeader`/`ModalBody` (SWAP, reused existing compat exports, no JSX changes), `StructuredListWrapper`/`StructuredListBody`/`StructuredListRow`/`StructuredListCell` (SWAP, new compat exports added), `EventSchedule`→`EventScheduleIcon`/`CalendarClock` (REMAP, new compat export added), `UserAvatar`→`UserAvatarIcon` (REMAP, reused existing compat export). No FLAG-tier symbols in this file.

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details-history/components/HistoryItemDetailsModal.module.scss`
- **Tokens found:** `--cds-spacing-02` (gap), `--cds-spacing-05` (padding)
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`.
- **Suggested approach:** replace with the DS spacing scale equivalents when this SCSS module is next touched.
- **Shim applied:** No (out of scope, not touched)

### Note on prior UserAvatar icon guidance

The 2026-07-22 deferral entry above (line 20 in this file) recorded `UserAvatar`→`User` (Lucide fallback, no registry match) as the researched choice for this file. When implementing, `src/shared/design-system-compat/index.ts` already had an established export for this exact Carbon symbol from an earlier migration (`UserAvatarIcon`, mapped to Lucide `CircleUser` — see the inline comment there for the outlined/filled rationale). Reused that existing export instead of adding a second, differently-mapped export for the same source icon, to avoid two Lucide icons representing the same Carbon glyph across the compat layer. No behavior change versus the researched choice (both are Lucide fallbacks with no registry match) — only the specific Lucide icon differs (`CircleUser` vs `User`).

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/pages/TaskDetailsHistoryErrorPage.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete). Migrated: `Button`, `Stack` (SWAP, reused existing compat exports, no JSX changes), `Layer` (SWAP/shim subtype — `carbon-compat` re-exports `Layer` unchanged from `@carbon/react`, new compat export added, no JSX changes), `Link` (SWAP, new compat export added, no JSX changes), `Launch`→`LaunchIcon`/`ExternalLink` (REMAP, new compat export added — matches the mapping confirmed in the 2026-07-22 deferral entry for this same file, above). No FLAG-tier symbols in this file.

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/pages/TaskDetailsHistoryErrorPage.module.scss`
- **Tokens found:** `--cds-spacing-07` (padding, `.container` and `.card` rules)
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`.
- **Suggested approach:** replace with the DS spacing scale equivalent when this SCSS module is next touched.
- **Shim applied:** No (out of scope, not touched)

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/process-diagram/DiagramControls.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete, near line-for-line against the legacy version). Migrated: `IconButton` (REMAP — `Button size="icon"` + `Tooltip` composition; no drop-in carbon-compat adapter exists, so a new composed wrapper was added at `src/shared/design-system-compat/IconButton.tsx` and re-exported through `index.ts`, rather than the plain ternary pattern used for other symbols in that file). Icons: `CenterCircle`→`CenterCircleIcon`/`Crosshair`, `Add`→`AddIcon`/`ZoomIn`, `Subtract`→`SubtractIcon`/`ZoomOut` (all REMAP, new compat exports added, Lucide fallback — no registry match; context-specific choice for diagram zoom controls, not the generic `carbon-icons-to-lucide.md` "Common remaps" row for bare `Add`). No FLAG-tier symbols in this file.

### IconButton wrapper — design notes

- `carbon-compat/MAPPING.md`'s "IconButton" row documents the full prop transform (`kind`→`variant`, `label`→ tooltip content + `aria-label` fallback, `align`→`TooltipContent side` prefix-mapped, `enterDelayMs`→`TooltipProvider delayDuration`, `autoAlign`/`badgeCount`/`highContrast` dropped) — the wrapper implements exactly this table, no invented behavior.
- The wrapper renders its own `TooltipProvider` per instance. The DS `Tooltip` primitive ships without a built-in provider by design (per its own source comment, it expects "exactly one `TooltipProvider` at the app/page root") — this repo (`main.tsx`) does not have one yet, and adding it is out of scope for a single-file migration. Sibling providers (not nested) don't trigger the open-state misfire the DS docs warn about, so this is safe short-term. **Follow-up:** once a repo-wide `TooltipProvider` exists at the app root, remove the per-instance provider from this wrapper and rely on the shared one.
- `IconButtonProps` in the wrapper is a deliberately narrower, explicitly-typed subset of Carbon's real `IconButtonProps` (native button attributes + `kind`/`size`/`align`/`label`/`enterDelayMs`) rather than a full pass-through of Carbon's type — this avoids forwarding anchor-only props (`href`, `rel`, `target`) that don't exist on `@camunda/design-system`'s `Button` (a native `<button>` component), which would otherwise be a type mismatch. `DiagramControls.tsx`'s actual usage (`className`, `size`, `kind`, `align`, `label`, `aria-label`, `onClick`, `children`) is fully covered.

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/process-diagram/DiagramControls.module.scss`
- **Tokens found:** `--cds-spacing-05`, `--cds-spacing-09`, `--cds-spacing-02` (positioning/margin), `--cds-background` (×2, button backgrounds)
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`.
- **Suggested approach:** replace with the DS spacing scale and `bg-background`/`var(--background)` equivalents when this SCSS module is next touched.
- **Shim applied:** No (out of scope, not touched)

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/pages/TaskDetailsProcessError.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete; commit history tails into "refactor: simplify error pages" — cleanup, not open construction). Migrated: `Button` (SWAP, reused existing compat export, no JSX changes), `InlineNotification` (REMAP tier per `docs/kb/carbon-migration-tiers.md`, but the shipped `carbon-compat` adapter is Carbon-shaped — its exported type is `CarbonInlineNotificationProps` verbatim, with `kind`/`subtitle`/`hideCloseButton`/`role` mapped internally — so this call site needed zero JSX changes, only the import swap; reused existing compat export). Both symbols already existed as feature-flagged exports in `src/shared/design-system-compat/index.ts` — no new exports added. No FLAG-tier symbols in this file.

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/pages/TaskDetailsProcessError.module.scss`
- **Tokens found:** `--cds-spacing-05` (gap, padding on `.container`)
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`.
- **Suggested approach:** replace with the DS spacing scale equivalent when this SCSS module is next touched.
- **Shim applied:** No (out of scope, not touched)

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/Task.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete; commit history tails into "fix: preserve filters on task navigation" and "refactor: simplify task component" — cleanup/fix, not open construction; the apparent size reduction is from extracting helpers into their own tested files, not missing logic). Migrated: `Stack` (SWAP, reused existing compat export, no JSX changes). Icons (all REMAP, `@carbon/react/icons` → `lucide-react`, per `docs/kb/carbon-icons-to-lucide.md`): `Calendar`→`CalendarIcon`/Lucide `Calendar` (identical name, new compat export added); `CheckmarkFilled`→`CheckmarkFilledIcon`/Lucide `CircleCheck` (new export already existed in `src/shared/design-system-compat/index.ts` from the concurrent `TaskDetailsHeader.tsx` migration — reused as-is, added `fill-current text-success-foreground-strong` at the call site per the export's own comment); `Warning`→`WarningIcon`/Lucide `TriangleAlert` (new compat export added, consistent with the existing `Critical`→`TriangleAlert` precedent already in the file; added `fill-current text-danger-foreground-strong` at the call site); `Notification`→`NotificationIcon`/Lucide `Bell` (new compat export added — genuine gap, not in `carbon-icons-to-lucide.md` at all; fallback tier; added `fill-current text-info-foreground-strong` at the call site — see "Icon gaps" entry below). No FLAG-tier symbols in this file.

### Icon gaps (Carbon → Lucide)

- **Carbon icon:** `Notification` (from `@carbon/react/icons`)
- **Context:** follow-up-date indicator icon in the task row date strip (`Task.tsx`)
- **Chosen Lucide fallback:** `Bell`
- **Reason:** not listed in `docs/kb/carbon-icons-to-lucide.md`'s mapping table (no domain-entity registry match, no generic-UI-icon row). `Bell` is the closest visual/semantic equivalent for a notification/reminder glyph — used per the KB's "no reasonable equivalent" fallback guidance.
- **Follow-up:** consider adding `Notification` → `Bell` as a documented row in `docs/kb/carbon-icons-to-lucide.md`'s "Common remaps" table so future migrations don't need to re-derive this choice.

### CSS token debt

- **File:** `webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/Task.module.scss`
- **Tokens found:** `--cds-spacing-05` (`$spacing`, used throughout `.container`/`.taskLink` padding and borders), `--cds-text-secondary` (`.label`), `--cds-text-primary` (`.name`), `--cds-layer-selected` (`.container.active .taskLink`), `--cds-border-interactive` (`.container.active .taskLink`), `--cds-layer-hover` (`.container:not(.active):hover .taskLink`), `--cds-border-subtle-selected` (×2), `--cds-focus` (`.taskLink:focus`)
- **Reason skipped:** `--cds-*` usage is inside a `.module.scss` file, not an inline `style=` value in JSX — out of scope for the inline-replacement audit per `docs/kb/carbon-css-token-audit.md`.
- **Suggested approach:** resolve to DS tokens/Tailwind when this SCSS module is next touched — `--cds-spacing-05` → the DS spacing scale, `--cds-text-secondary`/`--cds-text-primary` → `text-muted-foreground`/`text-foreground` (`var(--muted-foreground)`/`var(--foreground)`), `--cds-layer-hover`/`--cds-layer-selected` → nearest `bg-*` surface token, `--cds-border-interactive`/`--cds-border-subtle-selected`/`--cds-focus` → nearest `border-*`/`ring-*` token per the mapping table.
- **Shim applied:** No (out of scope, not touched)

## Scan: 2026-07-23 — webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/DetailsSkeleton.tsx

HOT hold lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete). Migrated: `SkeletonText` (SWAP, reused existing compat export, no JSX changes), `TabsSkeleton` (SWAP, new compat export added — `carbon-compat` ships a Tailwind `animate-pulse` adapter, no JSX changes), `ContainedList`/`ContainedListItem` (SHIM, new compat exports added — `carbon-compat` re-exports both unchanged from `@carbon/react`, no JSX changes), `Section` (SHIM, reused existing compat export added by a concurrent sibling migration, no JSX changes). `ButtonSkeleton` left on `@carbon/react` — see FLAG entry below.

## FLAG symbols

### webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details/components/DetailsSkeleton.tsx:29 — ButtonSkeleton

- **Tier:** FLAG
- **Reason:** No `carbon-compat` adapter exists for `ButtonSkeleton` — it is not exported anywhere under `@camunda/design-system/dist/carbon-compat` (confirmed by direct grep of the installed package). No DS equivalent to swap or shim to. Not silently approximated per FLAG protocol.
- **Suggested approach:** requires DS input — either ship a `ButtonSkeleton` adapter in `carbon-compat` (a button-shaped `animate-pulse` placeholder, mirroring the existing `TabsSkeleton` adapter pattern), or provide a DS-native loading-button skeleton. Once available, add a feature-flagged export to `src/shared/design-system-compat/index.ts` and repoint this file's remaining `@carbon/react` import.
- **Shim applied:** No
- **Closest DS component:** none shipped; nearest analog is the `carbon-compat` `TabsSkeleton` adapter (Tailwind `animate-pulse` placeholder) — same technique would apply to a button-shaped skeleton.
- **Current state in file:** import isolated to its own `@carbon/react` line with a `// FLAG:` comment; usage at the call site carries a `{/* FLAG: ... */}` marker. Import/usage left functionally untouched; the file still renders identically.

### webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/available-tasks/components/CollapsiblePanel.tsx:258 — ButtonSet

- **Tier:** FLAG
- **Reason:** No `carbon-compat` adapter exists for `ButtonSet` — it is not exported anywhere under `@camunda/design-system/dist/carbon-compat` (no `button-set.*` module; confirmed by direct grep of the installed package). No DS equivalent to swap or shim to. Not silently approximated per FLAG protocol.
- **Suggested approach:** requires DS input — either ship a `ButtonSet` adapter in `carbon-compat` (a flex/grid row wrapper matching Carbon's button-group spacing), or replace the single-child `ButtonSet` here with a plain DS layout wrapper. Note this call site wraps only one `Button`, so the grouping semantics are minimal — a DS layout primitive may suffice once the surrounding panel is fully migrated. Once available, add a feature-flagged export to `src/shared/design-system-compat/index.ts` and repoint this file's remaining `@carbon/react` import.
- **Shim applied:** No
- **Closest DS component:** none shipped as a 1:1 `ButtonSet`; a DS flex/stack layout primitive is the nearest analog for the single-button grouping used here.
- **Current state in file:** import isolated to its own `@carbon/react` line with a `// FLAG:` comment; usage at the call site carries a `{/* FLAG: ... */}` marker. Import/usage left functionally untouched; the file still renders identically. All other symbols in the file (`Button`, `OverflowMenu`, `OverflowMenuItem`, `Layer`, and the three `@carbon/react/icons` — `Filter`, `SidePanelClose`, `SidePanelOpen`) were migrated to the `#/shared/design-system-compat` swap point in the same pass (2026-07-23).

### webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details-history/components/HistoryTable.tsx:10-20,128-192 — DataTable (render-props) + TableContainer, Table, TableBody, TableCell, TableHead, TableRow

- **Tier:** FLAG (escalated — the 2026-07-23 pre-flight bucketed `DataTable`/`TableContainer` as SHIM and only `DataTableHeader` as FLAG; that is wrong for this file's actual usage, see below).
- **Reason:** This component is built entirely on Carbon's `DataTable` **render-props children** API — `<DataTable<RowData, RowCellValues> rows headers isSortable>{({rows, headers, getTableProps, getRowProps}) => (...)}</DataTable>` — and the render function is what wires up `getTableProps()`, `getRowProps()`, the per-cell model (`cell.info.header`, `cell.value`, `cell.id`), the custom `details`/assignee cell, and the `actions` info-link cell. The shipped `carbon-compat` `DataTable` adapter (`@camunda/design-system/dist/carbon-compat/data-table.js`) **cannot back this usage**, for two independent reasons confirmed by reading the adapter source:
  1. **Generic arity mismatch (hard tsc error).** Carbon's `DataTableProps<RowType, ColTypes extends any[]>` takes two type params; the compat adapter's `DataTable<TData extends DataTableRow>` takes one. The call site `DataTable<RowData, RowCellValues>` would raise `TS2558: Expected 1 type argument, but got 2` the instant the import is repointed.
  2. **Render-props not supported (silent runtime regression).** The adapter destructures `children`, fires a dev-only warning, and never calls the render function — it renders its own TanStack `DataTable` from `columns`/`data` derived only from `rows`/`headers`. Repointing would drop the entire custom render: the `ColumnHeader` sort headers, the assignee `details` cell, and the `Information`-link `actions` cell all vanish; the `actions` column would render the raw `auditLogKey` string (its `RowData.actions` value); and DS-internal sorting would conflict with the existing URL-driven sort in `ColumnHeader`. Because the `dsTasklistUI` flag defaults off, a snapshot-based visual gate taken with the flag off would **not** catch this — the regression only appears when the flag flips on.
- **Why the dependent Table family is also FLAG:** `TableContainer`, `Table`, `TableBody`, `TableCell`, `TableHead`, `TableRow` here are all rendered *inside* the Carbon `DataTable` render-props subtree and consume Carbon's `getTableProps()`/`getRowProps()`/cell context. The compat versions are shadcn-backed and drop those Carbon props (`warnDroppedProps`), so they cannot be swapped independently while the parent `DataTable` stays Carbon — mixing a Carbon `DataTable` render host with shadcn table primitives produces incoherent markup/styling. They move as one unit with `DataTable`.
- **Suggested approach:** requires design + engineering input — rewrite the component onto the DS declarative `DataTable` API (define `columns` with custom `cell` renderers for the assignee-`details` and info-link `actions` columns; move sorting to the adapter's `isSortable`/DS sorting props or keep the external URL-driven sort and render plain DS `Table` primitives without `DataTable`). Not a mechanical import swap. Not silently approximated per FLAG protocol.
- **Shim applied:** No.
- **Closest DS component:** `@camunda/design-system/carbon-compat` `DataTable` (declarative TanStack-based) — but only after a render-props → declarative rewrite; not a drop-in for this call site.
- **Current state in file:** the six table symbols plus `type DataTableHeader` remain imported from `@carbon/react` on their original block, now carrying a `// FLAG:` comment explaining the render-props incompatibility. Imports/usages left functionally untouched; the file renders identically with the flag on or off.

### webapp/client/apps/orchestration-cluster-webapp/src/tasklist/modules/task-details-history/components/HistoryTable.tsx:19 — DataTableHeader (type-only import)

- **Tier:** FLAG (as designated by the 2026-07-23 pre-flight).
- **Reason:** Type-only import (`import { ..., type DataTableHeader } from '@carbon/react'`) used to type the `headers` memo (`useMemo<DataTableHeader[]>`). It is bound to the Carbon `DataTable` render-props data model above; the compat package does export an unrelated `DataTableHeader` interface (`{key, header}`, from `carbon-compat/data-table`), but it is a different, narrower shape and is only meaningful with the declarative adapter — swapping the type without the render-props → declarative rewrite would be a false migration. Left with the FLAG'd table cluster it belongs to. Not silently approximated per FLAG protocol.
- **Suggested approach:** resolve together with the `DataTable` rewrite above — the header typing follows whichever data model the rewritten table adopts.
- **Shim applied:** No.
- **Closest DS component:** `DataTableHeader` type from `@camunda/design-system/carbon-compat` (only valid once the component moves to the declarative adapter).

### Migrated in the same 2026-07-23 pass (context for the FLAG entries above)

HOT hold on `HistoryTable.tsx` was lifted (investigation confirmed the legacy-to-unified-webapp migration underlying this file is code-complete — the last commit, "refactor: turn history details button into link", is targeted cleanup, not open construction). One symbol was safely migrated: `Information` (`@carbon/react/icons`) → `InformationIcon` / Lucide `Info` (REMAP, new feature-flagged export added to `src/shared/design-system-compat/index.ts`; `Info` is the direct "i-in-circle" equivalent — same glyph, no approximation). This icon is independent of the table render-props context (it renders inside the `actions`-cell `Link`), so it was migrated per the icon-priority rule even though its Carbon container stays FLAG. `tsc -p tsconfig.browser.json` is clean for both touched files (the 4 pre-existing errors in the concurrently-worked `custom-filters/FieldsModal.tsx` — a `DatePicker` `locale` type mismatch — are unrelated to this migration).
