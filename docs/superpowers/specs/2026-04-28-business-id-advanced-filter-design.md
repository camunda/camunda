# Business ID — list-item visibility and operator-aware filter

Branch: `3436-ui-business-id-visibility`
Status: Design (prototype)
Author: Zsofia Komaromi (`zsofia.komaromi@camunda.com`)
Date: 2026-04-28

## Goal

Restore Business ID visibility on the tasklist list view, and introduce operator-aware filtering for Business ID in Operate (Process Instances + Decisions) and tasklist (Advanced Filters). Operators mirror the variables-filter pattern from `3124-operate-filter-by-multiple-variables` (`$eq`, `$neq`, `$like`, `$exists`). UI prototype only — data is mocked, no backend changes.

## Out of scope

- Real API integration. Filter values pass through to mock pipelines and `// TODO: ...` comments mark the integration points.
- Tests. Existing tests broken by structural changes get the minimum touch needed to pass; **no new tests** are written for the new components. A follow-up TODO captures this.
- Variables filter. The operator-aware variables filter lives on `3124-operate-filter-by-multiple-variables`. This work does not pull it in or refactor toward it.
- Operate `InstancesTable` Business ID column styling (Process Instances and Decisions). Stays as plain string display.
- Operate `ProcessInstanceHeader` and `DecisionInstance/Header` Business ID display rows. Unchanged from the previous commit.

## 1. Tasklist list-item display

`tasklist/client/src/common/tasks/available-tasks/AvailableTaskItem/index.tsx`:

- Add `businessId?: string | null` back to `Props`.
- Render a third line inside the `flex flex-column` block that already holds `displayName` and `processDisplayName`:

  ```tsx
  {businessId ? (
    <span
      className={cn(styles.label, styles.labelPrimary)}
      data-testid="business-id"
    >
      {businessId}
    </span>
  ) : null}
  ```

- Uses the existing `labelPrimary` modifier already declared in `styles.module.scss`. No new style class. No icon. Same `label-01` typography as `processDisplayName`, but in `--cds-text-primary` instead of `--cds-text-secondary` for visual distinction.
- Hidden when `businessId` is null/undefined/empty.

`tasklist/client/src/v1/TasksTab/AvailableTasks/index.tsx` and `v2/TasksTab/AvailableTasks/index.tsx`: re-pass the mocked `businessId` prop with the existing `// TODO: Replace with actual businessId from API response (task.businessId)` comment.

`tasklist/client/src/common/tasks/details/Aside/index.tsx`: unchanged from the current state — Business ID stays as the **last** entry in the `ContainedList`.

## 2. Operate Business ID filter component

New component directory: `operate/client/src/App/Processes/ListView/Filters/BusinessIdFilter/`

Files:

- `index.tsx` — controlled inline row.
- `constants.ts` — operator type, operator config array, API-shape helper.
- `styled.tsx` — row layout container.

Reused by Decisions via direct import. The component reads/writes its two URL params indirectly through Final Form: it consumes `useForm()` plus `<Field name="businessIdOperator">` and `<Field name="businessId">`, and the surrounding `OptionalFiltersFormGroup` form is what serializes those values to and from the URL. Because both pages mount the same Final Form shape (a flat object keyed by URL-param name), the component drops in identically on both.

### 2.1 Layout

Single inline row inside the existing optional-filter `FieldContainer`:

```
[ Operator dropdown (sm, ~140px fixed) ]   [ Value input (sm, fills remaining width) ]
```

- Operator: Carbon `Dropdown`, `size="sm"`, `titleText=""`, `aria-label="Business ID condition"`, `data-testid="business-id-operator"`.
- Value: Carbon `TextInput`, `size="sm"`, `labelText="Business ID"`, `hideLabel` (the row has the field's label from the Optional Filters chrome already), `data-testid="business-id-value"`.
- Value input is **not rendered** when operator is `exists` or `doesNotExist`. Row collapses to dropdown only.

### 2.2 Operators

```ts
// constants.ts
export type BusinessIdFilterOperator =
  | 'equals'         // $eq
  | 'notEqual'       // $neq
  | 'contains'       // $like, auto-wrap as *value*
  | 'exists'         // $exists: true
  | 'doesNotExist';  // $exists: false

export const BUSINESS_ID_FILTER_OPERATORS: Array<{
  id: BusinessIdFilterOperator;
  label: string;
  requiresValue: boolean;
}> = [
  {id: 'equals', label: 'equals', requiresValue: true},
  {id: 'notEqual', label: 'not equal', requiresValue: true},
  {id: 'contains', label: 'contains', requiresValue: true},
  {id: 'exists', label: 'exists', requiresValue: false},
  {id: 'doesNotExist', label: 'does not exist', requiresValue: false},
];
```

Default operator (when the filter is added from the Optional Filters menu): `equals`.

### 2.3 API-shape helper

In `constants.ts`, exported alongside the operator types:

```ts
export function buildBusinessIdFilterValue(
  operator: BusinessIdFilterOperator,
  value: string,
): Record<string, unknown> | undefined {
  switch (operator) {
    case 'equals':       return value === '' ? undefined : {$eq: value};
    case 'notEqual':     return value === '' ? undefined : {$neq: value};
    case 'contains':     return value === '' ? undefined : {$like: `*${value}*`};
    case 'exists':       return {$exists: true};
    case 'doesNotExist': return {$exists: false};
  }
}
```

Returns `undefined` when the filter is inactive (empty value with a value-required operator). Auto-wrap with `*` is intentional and mirrors the variables-branch behavior; raw wildcard input is not exposed on this branch.

### 2.4 Wiring

`operate/client/src/App/Processes/ListView/Filters/OptionalFiltersFormGroup.tsx`:

- The optional-filter renderer is a `switch (filter)` that today routes `businessId` through the generic `default` arm (which renders a Final Form `<Field>` driven by `OPTIONAL_FILTER_FIELDS[filter]`). Add an explicit `case 'businessId':` branch that returns `<BusinessIdFilter />` instead, mirroring how `case 'variable':` and the date-range cases are handled today.
- Extend `OPTIONAL_FILTER_FIELDS.businessId.keys` from `['businessId']` to `['businessId', 'businessIdOperator']`, so the X-button removal flow clears both URL params via the existing `form.change(key, undefined)` loop.

`operate/client/src/App/Decisions/Filters/OptionalFiltersFormGroup.tsx`: same swap and same `keys` extension.

### 2.5 URL state shape

Two query params (both optional):

- `businessId` — string, the value. Omitted only when operator is `equals` with an empty value (filter inactive). When operator is `exists` or `doesNotExist`, any previously typed value is **preserved** in the URL so switching back to a value-required operator restores it; the value is just visually hidden and ignored by the API-shape helper.
- `businessIdOperator` — one of `equals | notEqual | contains | exists | doesNotExist`. Omitted whenever operator is `equals` (regardless of whether `businessId` has a value), so legacy bookmarks of the form `?businessId=foo` from the previous commit continue to render correctly. Written to the URL only when operator is one of `notEqual | contains | exists | doesNotExist`.

Parse rules (in `getProcessInstanceFilters` / `decisionsFilter`):

- Both absent → filter inactive.
- Only `businessId` present → operator defaults to `equals`.
- Only `businessIdOperator=exists` or `=doesNotExist` present → render row with operator selected, no value.
- Unknown `businessIdOperator` value → fall back to `equals` rather than throwing.

Type changes:

- `operate/client/src/modules/utils/filter/shared.ts`: add `'businessIdOperator'` to `ProcessInstanceFilterField`.
- `operate/client/src/modules/utils/filter/decisionsFilter.ts`: add `businessIdOperator` to the decisions filter type analogously.

## 3. Tasklist Business ID field (Advanced Filters)

`tasklist/client/src/common/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal/FieldsModal.tsx`:

- New section directly **below** the existing Task ID `<Field name="taskId">` block, before the variables FieldArray. Renders only when the Advanced Filters toggle is on (already wrapped in the same `{values?.areAdvancedFiltersEnabled ? ... : null}` branch).
- Two adjacent fields: `businessIdOperator` (Carbon `Dropdown`, default `equals`) and `businessId` (Carbon `TextInput`).
- Value field hidden when operator is `exists`/`doesNotExist`. Same `requiresValue` check as Section 2.

State shape:

- `NamedCustomFilters` type: add `businessId?: string` and `businessIdOperator?: BusinessIdFilterOperator`.
- `namedCustomFiltersSchema` (zod): add the two optional fields. No required validation.
- `ADVANCED_FILTERS` array (line ~53): append `'businessId'` and `'businessIdOperator'` so the toggle's "is this advanced?" detection still works when a saved filter is restored.

Operator constants:

- `tasklist/client/src/common/tasks/filters/businessIdOperators.ts` — local mirror of the Operate constants. Same operator IDs and labels. Sharing across apps is out of scope on this branch.

i18n keys (added to `de.json`, `en.json`, `es.json`, `fr.json`):

- `customFiltersModalBusinessIdLabel`
- `customFiltersModalBusinessIdOperatorLabel`
- `filterOperatorEquals`, `filterOperatorNotEqual`, `filterOperatorContains`, `filterOperatorExists`, `filterOperatorDoesNotExist`

No filter side effects:

- The two values flow through `onApply`/`onSave`/`onEdit` callbacks unchanged. The downstream consumer (the URL/store pipeline driving `useTaskFilters`) ignores them. A `// TODO: wire up business ID filtering to API ($eq/$neq/$like/$exists)` comment marks the integration point on the consumer side.

## 4. Edge cases

- **Operator switch with value present**: the value stays in component state across operator changes; switching to `exists`/`doesNotExist` only hides the input visually, switching back restores it.
- **Empty value with value-required operator**: filter is inactive (URL omits both params, list unfiltered). No inline error.
- **`*`/`?` typed inside `contains` value**: passed through to the auto-wrap as-is. No escaping.
- **Operator-only URL** (`?businessIdOperator=exists`): valid — renders the row with `exists` selected, value field hidden.
- **Legacy URL** (`?businessId=foo`): treated as `equals` + `foo`.

## 5. Validation

- No new validators on the value field. Free-form text, mirroring the previous commit's behavior.
- Operator is constrained by the `Dropdown` items list; URL-parse fall-back handles unknown values.

## 6. Follow-ups (TODOs)

- **Tests for the new `BusinessIdFilter` component** (Operate) and the new fields in `FieldsModal` (tasklist). Skipped on this prototype branch per the team's design-prototype guidance in `CLAUDE.md`. Track as a follow-up before any non-prototype merge.
- **Real API integration**. Today the filter values feed into the existing mocked filter pipeline and `// TODO: Replace with API call ...` comments. The `buildBusinessIdFilterValue` helper produces the API-shaped object so the integration is a one-line swap when the backend is ready.
- **Shared operator constants**. Operate has `BusinessIdFilter/constants.ts`; tasklist has `common/tasks/filters/businessIdOperators.ts`. If Business ID and variables filtering converge across apps, lift to a shared module (likely under `webapps-common/`) at that point — not now.
- **Decisions filter parity**. Implemented in this work, but verify with the Decisions team that the same operator set is meaningful for decision-instance lookups (Business ID semantics may differ subtly between process instances and decision instances).

## 7. Open question — operator scope across all filterable fields

The epics describing this work talk about logical operators (`$eq`, `$neq`, `$exists`, `$like`, etc.) for filtering. This prototype scopes them to **Business ID only**, but if the API supports operators for all filterable fields, the same UX likely belongs on **every** optional filter — Process Instance Key, Operation ID, Parent Process Instance Key, Error Message, etc. — not just Business ID.

Outstanding question for the product / API discussion:

- Does the v2 search API expose advanced-string-filter / advanced-date-filter operators uniformly across filterable fields? (`AdvancedStringFilter` from `@camunda/camunda-api-zod-schemas` suggests yes for string fields.)
- If yes, what is the expected rollout — do we promote the per-field operator UX (the `[Dropdown] [TextInput]` row pattern from `BusinessIdFilter`) to a shared component used by every text-typed optional filter, and at what point in the roadmap?
- Are there fields that *shouldn't* expose operators (e.g. boolean filters like `hasRetriesLeft`, single-select state filters like `active/incidents/completed/canceled`)? Almost certainly yes — but the line should be drawn explicitly rather than per ad-hoc PR.

Resolving this informs whether the `BusinessIdFilter` component should be lifted into a generic `OperatorAwareTextFilter` (reusable) before any second filter adopts the pattern, or whether Business ID stays a one-off until the API contract is broader.
