# Business ID — list-item visibility and operator-aware filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-add Business ID to the tasklist list view (with a typographic primary-color distinction, no icon) and add operator-aware Business ID filtering (`equals`, `not equal`, `contains`, `exists`, `does not exist` mapping to `$eq`/`$neq`/`$like`/`$exists`) to Operate Process Instances + Decisions and tasklist Advanced Filters.

**Architecture:** UI prototype, no backend wiring. A new `BusinessIdFilter` component lives under `operate/client/src/App/Processes/ListView/Filters/BusinessIdFilter/` and is reused by both Operate pages — it reads/writes `businessId` and `businessIdOperator` URL params via the surrounding Final Form, exposes a `[Dropdown] [TextInput]` row inline, and hides the value field for `exists`/`doesNotExist`. Tasklist gets matching operator constants under `common/tasks/filters/businessIdOperators.ts` and adds two new fields (`businessId`, `businessIdOperator`) to the existing `CustomFiltersModal/FieldsModal.tsx` Advanced Filters branch — no filter side effects (a TODO comment marks the integration point).

**Tech Stack:** React 18, TypeScript, Carbon Design System (`@carbon/react`), Final Form, MobX (Operate filter store, untouched), Zod (tasklist + Operate schema), Vite, Vitest (skipped per prototype guidance — no new tests).

**Reference design:** `docs/superpowers/specs/2026-04-28-business-id-advanced-filter-design.md`

**Reference branch (read-only):** `3124-operate-filter-by-multiple-variables` — multi-variable filter pattern (`VariableFilterRow`, `constants.ts`, operator labels). Operator subset and `requiresValue` toggle behavior are copied verbatim.

---

## File Structure

**Created:**
- `operate/client/src/App/Processes/ListView/Filters/BusinessIdFilter/index.tsx` — controlled inline row component
- `operate/client/src/App/Processes/ListView/Filters/BusinessIdFilter/constants.ts` — operator type, operator config array, `buildBusinessIdFilterValue` helper
- `operate/client/src/App/Processes/ListView/Filters/BusinessIdFilter/styled.tsx` — row layout container
- `tasklist/client/src/common/tasks/filters/businessIdOperators.ts` — tasklist-local operator constants

**Modified:**
- `tasklist/client/src/common/tasks/available-tasks/AvailableTaskItem/index.tsx` — re-add `businessId` prop + render line
- `tasklist/client/src/v1/TasksTab/AvailableTasks/index.tsx` — re-pass mocked `businessId`
- `tasklist/client/src/v2/TasksTab/AvailableTasks/index.tsx` — re-pass mocked `businessId`
- `operate/client/src/modules/utils/filter/shared.ts` — add `businessIdOperator` field
- `operate/client/src/modules/utils/filter/decisionsFilter.ts` — add `businessIdOperator` to schema
- `operate/client/src/App/Processes/ListView/Filters/OptionalFiltersFormGroup.tsx` — add `case 'businessId':` switch arm + extend `keys`
- `operate/client/src/App/Decisions/Filters/OptionalFiltersFormGroup.tsx` — wrap text-field branch with `case 'businessId':` + extend `keys`
- `tasklist/client/src/common/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal/FieldsModal.tsx` — add Business ID + operator fields, extend `ADVANCED_FILTERS`
- `tasklist/client/src/common/tasks/filters/customFiltersSchema.ts` — extend zod schema
- `tasklist/client/src/common/i18n/locales/{de,en,es,fr}.json` — new i18n keys
- `operate/client/src/App/Processes/ListView/Filters/tests/{index,fieldinteractions,optionalFilters,validations}.test.tsx` — only **minimal** updates if a test fails because of structural change

---

## Phase 1 — Tasklist list view: re-add Business ID

### Task 1.1: Re-add `businessId` prop and render line to `AvailableTaskItem`

**Files:**
- Modify: `tasklist/client/src/common/tasks/available-tasks/AvailableTaskItem/index.tsx`

- [ ] **Step 1: Re-add `businessId` to Props**

In the `Props` type (currently lines ~38–52), add `businessId?: string | null;` directly under `context?: string | null;`:

```ts
type Props = {
  taskId: string;
  displayName: string;
  processDisplayName: string;
  context?: string | null;
  businessId?: string | null;
  assignee: string | null | undefined;
  creationDate: string;
  followUpDate: string | null | undefined;
  dueDate: string | null | undefined;
  completionDate: string | null | undefined;
  priority: number | null;
  currentUser: CurrentUser;
  position: number;
};
```

- [ ] **Step 2: Add `businessId` to the destructured props**

Inside the `forwardRef` callback (currently lines ~54–72), add `businessId = null,` directly under `context = null,`:

```ts
const AvailableTaskItem = React.forwardRef<HTMLDivElement, Props>(
  (
    {
      taskId,
      displayName,
      processDisplayName,
      context = null,
      businessId = null,
      assignee,
      creationDate: creationDateString,
      followUpDate: followUpDateString,
      dueDate: dueDateString,
      completionDate: completionDateString,
      priority,
      currentUser,
      position,
    },
    ref,
  ) => {
```

- [ ] **Step 3: Render the businessId line in the metadata column**

Replace the existing `<div className={cn(styles.flex, styles.flexColumn)}>` block (the one containing `displayName` and `processDisplayName`, around lines ~129–131) with:

```tsx
<div className={cn(styles.flex, styles.flexColumn)}>
  <span className={styles.name}>{displayName}</span>
  <span className={styles.label}>{processDisplayName}</span>
  {businessId ? (
    <span
      className={cn(styles.label, styles.labelPrimary)}
      data-testid="business-id"
    >
      {businessId}
    </span>
  ) : null}
</div>
```

The `cn` utility is already imported at the top of the file. The `labelPrimary` modifier exists in `styles.module.scss` (line ~17–19) and renders `--cds-text-primary` while keeping the `label-01` typography.

- [ ] **Step 4: Verify lint and types**

Run: `cd tasklist/client && npm run lint`
Expected: PASS — no errors. (Lint also runs the TypeScript compiler in this project.)

- [ ] **Step 5: Commit**

```bash
git add tasklist/client/src/common/tasks/available-tasks/AvailableTaskItem/index.tsx
git commit -m "feat: re-add Business ID line to tasklist list item"
```

---

### Task 1.2: Re-pass mocked `businessId` from v1 and v2 task lists

**Files:**
- Modify: `tasklist/client/src/v1/TasksTab/AvailableTasks/index.tsx`
- Modify: `tasklist/client/src/v2/TasksTab/AvailableTasks/index.tsx`

- [ ] **Step 1: Re-add mock prop in v1**

In `tasklist/client/src/v1/TasksTab/AvailableTasks/index.tsx`, find the `<AvailableTaskItem ...>` element (around line 85) and re-add the two lines that were removed earlier. The block should look like:

```tsx
<AvailableTaskItem
  ref={taskRef}
  key={task.id}
  taskId={task.id}
  displayName={task.name}
  processDisplayName={task.processName}
  context={task.context}
  // TODO: Replace with actual businessId from API response (task.businessId)
  businessId={`ORDER-${task.id.slice(-6)}`}
  assignee={task.assignee}
```

- [ ] **Step 2: Re-add mock prop in v2**

In `tasklist/client/src/v2/TasksTab/AvailableTasks/index.tsx`, find the `<AvailableTaskItem ...>` element (around line 90) and re-add the two lines. The block should look like:

```tsx
<AvailableTaskItem
  ref={taskRef}
  key={task.userTaskKey}
  taskId={task.userTaskKey.toString()}
  displayName={task.name ?? task.elementId}
  processDisplayName={
    task.processName ?? task.processDefinitionId
  }
  // TODO: Replace with actual businessId from API response (task.businessId)
  businessId={`ORDER-${task.userTaskKey.toString().slice(-6)}`}
  assignee={task.assignee}
```

- [ ] **Step 3: Verify lint and types**

Run: `cd tasklist/client && npm run lint`
Expected: PASS — no errors.

- [ ] **Step 4: Smoke test in dev server**

Run: `cd tasklist/client && npm run start`
Expected: tasklist boots; in the available tasks list, each task card shows three stacked text lines — task name, process name (gray), Business ID (`ORDER-XXXXXX`, primary color). Stop the server with Ctrl+C.

- [ ] **Step 5: Commit**

```bash
git add tasklist/client/src/v1/TasksTab/AvailableTasks/index.tsx tasklist/client/src/v2/TasksTab/AvailableTasks/index.tsx
git commit -m "feat: re-pass mocked Business ID prop to tasklist list items"
```

---

## Phase 2 — Operate: operator constants and API-shape helper

### Task 2.1: Create `BusinessIdFilter/constants.ts`

**Files:**
- Create: `operate/client/src/App/Processes/ListView/Filters/BusinessIdFilter/constants.ts`

- [ ] **Step 1: Write the constants file**

Create the file with this exact content:

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Operator labels and IDs for the Business ID filter.
 * Mirrors the variables-filter operator pattern from
 * `3124-operate-filter-by-multiple-variables` (with `is one of` removed,
 * since Business ID is a single value).
 *
 * Mapping to API operators (used by buildBusinessIdFilterValue):
 *   equals       → {$eq: value}
 *   notEqual     → {$neq: value}
 *   contains     → {$like: "*value*"} (auto-wrap)
 *   exists       → {$exists: true}
 *   doesNotExist → {$exists: false}
 */
export type BusinessIdFilterOperator =
  | 'equals'
  | 'notEqual'
  | 'contains'
  | 'exists'
  | 'doesNotExist';

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

export const DEFAULT_BUSINESS_ID_OPERATOR: BusinessIdFilterOperator = 'equals';

/**
 * Returns the operator config for a given ID, falling back to the default
 * (`equals`) when the ID is unknown — used when parsing URL search params.
 */
export function resolveBusinessIdOperator(
  raw: string | undefined,
): BusinessIdFilterOperator {
  const match = BUSINESS_ID_FILTER_OPERATORS.find((op) => op.id === raw);
  return match ? match.id : DEFAULT_BUSINESS_ID_OPERATOR;
}

/**
 * Builds the API-shaped filter value for a given (operator, value) pair.
 * Returns `undefined` when the filter is inactive (empty value with a
 * value-required operator).
 *
 * TODO: Replace mock data with an actual API call to
 * POST /v2/process-instances/search using the returned object as
 * `filter.businessId`.
 */
export function buildBusinessIdFilterValue(
  operator: BusinessIdFilterOperator,
  value: string,
): Record<string, unknown> | undefined {
  switch (operator) {
    case 'equals':
      return value === '' ? undefined : {$eq: value};
    case 'notEqual':
      return value === '' ? undefined : {$neq: value};
    case 'contains':
      return value === '' ? undefined : {$like: `*${value}*`};
    case 'exists':
      return {$exists: true};
    case 'doesNotExist':
      return {$exists: false};
  }
}
```

- [ ] **Step 2: Verify the file type-checks**

Run: `cd operate/client && npm run lint`
Expected: PASS — the file has no consumers yet, so no broken references.

- [ ] **Step 3: Commit**

```bash
git add operate/client/src/App/Processes/ListView/Filters/BusinessIdFilter/constants.ts
git commit -m "feat: add Business ID filter operator constants and API-shape helper"
```

---

## Phase 3 — Operate: `BusinessIdFilter` component

### Task 3.1: Create `BusinessIdFilter/styled.tsx`

**Files:**
- Create: `operate/client/src/App/Processes/ListView/Filters/BusinessIdFilter/styled.tsx`

- [ ] **Step 1: Write the styled module**

```tsx
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const FilterRow = styled.div`
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: var(--cds-spacing-03);
  align-items: end;
`;

const OperatorOnlyRow = styled.div`
  display: grid;
  grid-template-columns: 140px;
`;

export {FilterRow, OperatorOnlyRow};
```

`styled-components` is already a project dependency (used throughout Operate).

- [ ] **Step 2: Verify lint passes**

Run: `cd operate/client && npm run lint`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add operate/client/src/App/Processes/ListView/Filters/BusinessIdFilter/styled.tsx
git commit -m "feat: add Business ID filter row layout styles"
```

---

### Task 3.2: Create the `BusinessIdFilter` component

**Files:**
- Create: `operate/client/src/App/Processes/ListView/Filters/BusinessIdFilter/index.tsx`

- [ ] **Step 1: Write the component**

```tsx
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, useForm} from 'react-final-form';
import {Dropdown} from '@carbon/react';
import {TextInputField} from 'modules/components/TextInputField';
import {
  type BusinessIdFilterOperator,
  BUSINESS_ID_FILTER_OPERATORS,
  DEFAULT_BUSINESS_ID_OPERATOR,
  resolveBusinessIdOperator,
} from './constants';
import * as Styled from './styled';

/**
 * Inline operator + value row for filtering by Business ID.
 *
 * Reads/writes two URL params via the surrounding Final Form:
 *   businessId          – the value (omitted when operator is `exists`,
 *                         `doesNotExist`, or empty with a value-required
 *                         operator)
 *   businessIdOperator  – the operator id (omitted when operator is
 *                         `equals`, so legacy `?businessId=foo` URLs from
 *                         before this filter shipped continue to work)
 *
 * The value field is hidden for `exists` and `doesNotExist`. When the user
 * switches operator, any typed value is preserved in the form state; it just
 * becomes inactive while the value-less operator is selected.
 */
const BusinessIdFilter: React.FC = () => {
  const form = useForm();

  return (
    <Field name="businessIdOperator">
      {({input: operatorInput}) => {
        const rawValue =
          typeof operatorInput.value === 'string'
            ? operatorInput.value
            : undefined;
        const operatorId = resolveBusinessIdOperator(rawValue);
        const operatorConfig =
          BUSINESS_ID_FILTER_OPERATORS.find((op) => op.id === operatorId) ??
          BUSINESS_ID_FILTER_OPERATORS[0];
        const isValueRequired = operatorConfig.requiresValue;

        const RowComponent = isValueRequired
          ? Styled.FilterRow
          : Styled.OperatorOnlyRow;

        return (
          <RowComponent>
            <Dropdown<(typeof BUSINESS_ID_FILTER_OPERATORS)[number]>
              id="businessIdOperator"
              titleText="Business ID condition"
              hideLabel
              label="Select condition"
              size="sm"
              items={BUSINESS_ID_FILTER_OPERATORS}
              itemToString={(item) => item?.label ?? ''}
              selectedItem={operatorConfig}
              onChange={({selectedItem}) => {
                const nextId =
                  selectedItem?.id ?? DEFAULT_BUSINESS_ID_OPERATOR;
                // For the default operator, omit the param so legacy
                // `?businessId=foo` URLs round-trip cleanly.
                operatorInput.onChange(
                  nextId === DEFAULT_BUSINESS_ID_OPERATOR ? undefined : nextId,
                );
                // The typed `businessId` value is intentionally NOT cleared
                // when switching to `exists` / `doesNotExist`: it stays in
                // the URL and form state so that switching back to a
                // value-required operator restores what the user typed. The
                // value is hidden visually (the input does not render) and
                // ignored by buildBusinessIdFilterValue.
                form.submit();
              }}
              data-testid="business-id-operator"
            />
            {isValueRequired ? (
              <Field name="businessId">
                {({input: valueInput}) => (
                  <TextInputField
                    {...valueInput}
                    id="businessId"
                    size="sm"
                    labelText="Business ID"
                    hideLabel
                    placeholder="Business ID"
                    autoFocus
                    data-testid="business-id-value"
                  />
                )}
              </Field>
            ) : null}
          </RowComponent>
        );
      }}
    </Field>
  );
};

export {BusinessIdFilter};
```

A note on `TextInputField`: it forwards `data-testid` and `hideLabel` props through to Carbon's `TextInput`. Confirm by reading `operate/client/src/modules/components/TextInputField/index.tsx` if needed — the `Variable` field at `Filters/VariableField/index.tsx` uses the same pattern.

- [ ] **Step 2: Verify the component type-checks**

Run: `cd operate/client && npm run lint`
Expected: PASS.

If `TextInputField` does not forward `hideLabel` or `data-testid`, drop those two props (the component will still render correctly without them; the surrounding `FieldContainer` already provides labelling chrome via its own React tree). Leave the rest of the component unchanged.

- [ ] **Step 3: Commit**

```bash
git add operate/client/src/App/Processes/ListView/Filters/BusinessIdFilter/index.tsx
git commit -m "feat: add Business ID filter component"
```

---

## Phase 4 — Operate: schema and Process Instances wiring

### Task 4.1: Add `businessIdOperator` to the Process Instances filter type

**Files:**
- Modify: `operate/client/src/modules/utils/filter/shared.ts`

- [ ] **Step 1: Add the field to the type union**

In `ProcessInstanceFilterField` (currently lines 9–30), add `'businessIdOperator'` immediately after `'businessId'`:

```ts
type ProcessInstanceFilterField =
  | 'processDefinitionId'
  | 'processDefinitionVersion'
  | 'processInstanceKey'
  | 'businessId'
  | 'businessIdOperator'
  | 'parentProcessInstanceKey'
  | 'errorMessage'
  | 'incidentErrorHashCode'
  | 'elementId'
  | 'variableName'
  | 'variableValues'
  | 'batchOperationId'
  | 'active'
  | 'incidents'
  | 'completed'
  | 'canceled'
  | 'startDateFrom'
  | 'startDateTo'
  | 'endDateFrom'
  | 'endDateTo'
  | 'tenantId'
  | 'hasRetriesLeft';
```

- [ ] **Step 2: Add the field to the filters object type**

In `ProcessInstanceFilters` (currently lines 32–54), add `businessIdOperator?: string;` immediately after `businessId?: string;`:

```ts
type ProcessInstanceFilters = {
  processDefinitionId?: string;
  processDefinitionVersion?: string;
  processInstanceKey?: string;
  businessId?: string;
  businessIdOperator?: string;
  parentProcessInstanceKey?: string;
  // ... rest unchanged
};
```

- [ ] **Step 3: Add the field to the runtime list**

In `PROCESS_INSTANCE_FILTER_FIELDS` (currently lines 56–78), add `'businessIdOperator',` immediately after `'businessId',`:

```ts
const PROCESS_INSTANCE_FILTER_FIELDS: ProcessInstanceFilterField[] = [
  'processDefinitionId',
  'processDefinitionVersion',
  'processInstanceKey',
  'businessId',
  'businessIdOperator',
  'parentProcessInstanceKey',
  // ... rest unchanged
];
```

- [ ] **Step 4: Verify lint passes**

Run: `cd operate/client && npm run lint`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add operate/client/src/modules/utils/filter/shared.ts
git commit -m "feat: add businessIdOperator to process instance filter schema"
```

---

### Task 4.2: Wire `BusinessIdFilter` into the Process Instances optional filters

**Files:**
- Modify: `operate/client/src/App/Processes/ListView/Filters/OptionalFiltersFormGroup.tsx`

- [ ] **Step 1: Import the component**

Add this import alongside the other local imports near the top of the file (after `import {Variable} from './VariableField';`, currently line 39):

```tsx
import {BusinessIdFilter} from './BusinessIdFilter';
```

- [ ] **Step 2: Extend the `keys` array for the businessId field**

In the `OPTIONAL_FILTER_FIELDS` map (currently around line 90), update the `businessId` entry from:

```ts
businessId: {
  keys: ['businessId'],
  label: 'Business ID',
  type: 'text',
},
```

to:

```ts
businessId: {
  keys: ['businessId', 'businessIdOperator'],
  label: 'Business ID',
},
```

(Removing `type: 'text'` because the new render path is the dedicated component, not the generic text field — keeping the property would route the filter through the default switch arm and double-render.)

- [ ] **Step 3: Add an explicit `case 'businessId':` switch arm**

In the `switch (filter)` block (currently around line 204), add a new case immediately after `case 'variable':`:

```tsx
switch (filter) {
  case 'variable':
    return <Variable />;
  case 'businessId':
    return <BusinessIdFilter />;
  case 'startDateRange':
    return (
      // ... existing DateRangeField
    );
```

- [ ] **Step 4: Verify lint and types**

Run: `cd operate/client && npm run lint`
Expected: PASS.

- [ ] **Step 5: Smoke test in dev server**

Run: `cd operate/client && npm run start`

In the running app:
1. Open Process Instances.
2. Click the optional-filters menu → select **Business ID**. The row appears with `equals` selected and an empty value input.
3. Type `ORDER-123`, blur. URL gains `?businessId=ORDER-123` (no `businessIdOperator` param, since `equals` is the default).
4. Open the operator dropdown, pick **contains**. URL gains `&businessIdOperator=contains`. Value input still visible with `ORDER-123`.
5. Pick **exists**. Value input collapses out of the row. URL still contains `businessId=ORDER-123` (preserved by design, see component comment) plus `businessIdOperator=exists`.
6. Pick **equals** again. `businessIdOperator` param is removed from URL; `businessId=ORDER-123` and the value input return.
7. Click the X on the optional filter. Both params clear.

Stop the server with Ctrl+C.

- [ ] **Step 6: Commit**

```bash
git add operate/client/src/App/Processes/ListView/Filters/OptionalFiltersFormGroup.tsx
git commit -m "feat: wire Business ID operator filter into Process Instances"
```

---

## Phase 5 — Operate: Decisions wiring

### Task 5.1: Add `businessIdOperator` to the decisions filter schema

**Files:**
- Modify: `operate/client/src/modules/utils/filter/decisionsFilter.ts`

- [ ] **Step 1: Extend the zod schema**

In `DecisionsFilterSchema` (currently lines 29–42), add `businessIdOperator: z.string().optional(),` immediately after `businessId: z.string().optional(),`:

```ts
const DecisionsFilterSchema = z
  .object({
    decisionDefinitionId: z.string().optional(),
    decisionDefinitionVersion: z.string().optional(),
    evaluated: z.coerce.boolean().optional(),
    failed: z.coerce.boolean().optional(),
    decisionEvaluationInstanceKey: z.string().optional(),
    businessId: z.string().optional(),
    businessIdOperator: z.string().optional(),
    processInstanceKey: z.string().optional(),
    evaluationDateTo: z.string().transform(formatToISO).optional(),
    evaluationDateFrom: z.string().transform(formatToISO).optional(),
    tenantId: z.string().optional(),
  })
  .catch({});
```

`DECISION_INSTANCE_FILTER_FIELDS` is derived from the schema via `Object.values(DecisionsFilterSchema.unwrap().keyof().enum)` (line 157), so it picks up the new field automatically — no manual list update required.

- [ ] **Step 2: Verify lint and types**

Run: `cd operate/client && npm run lint`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add operate/client/src/modules/utils/filter/decisionsFilter.ts
git commit -m "feat: add businessIdOperator to decisions filter schema"
```

---

### Task 5.2: Wire `BusinessIdFilter` into Decisions optional filters

**Files:**
- Modify: `operate/client/src/App/Decisions/Filters/OptionalFiltersFormGroup.tsx`

- [ ] **Step 1: Import the component**

Add this import alongside the other local imports near the top:

```tsx
import {BusinessIdFilter} from 'App/Processes/ListView/Filters/BusinessIdFilter';
```

(Cross-page import. Consistent with how other Operate pages reuse `App/Processes/...` components such as `DateRangeField`.)

- [ ] **Step 2: Extend the `keys` array for the businessId field**

In the `OPTIONAL_FILTER_FIELDS` map (around line 75), update the `businessId` entry from:

```ts
businessId: {
  keys: ['businessId'],
  label: 'Business ID',
  type: 'text',
},
```

to:

```ts
businessId: {
  keys: ['businessId', 'businessIdOperator'],
  label: 'Business ID',
},
```

- [ ] **Step 3: Branch the renderer for `businessId`**

The decisions optional-filter renderer is currently a single `filter === 'evaluationDateRange' ? ... : <Field>...`. Replace that ternary with a `switch (filter)` block — find the section (around line 165) that reads:

```tsx
{filter === 'evaluationDateRange' ? (
  <DateRangeField
    isModalOpen={isDateRangeModalOpen}
    onModalClose={() => setIsDateRangeModalOpen(false)}
    onClick={() => setIsDateRangeModalOpen(true)}
    filterName={filter}
    popoverTitle="Filter decisions by evaluation date"
    label={OPTIONAL_FILTER_FIELDS[filter].label}
    fromDateTimeKey="evaluationDateFrom"
    toDateTimeKey="evaluationDateTo"
  />
) : (
  <Field
    name={filter}
    validate={OPTIONAL_FILTER_FIELDS[filter].validate}
  >
    {({input}) => {
      // ... existing TextInputField / TextAreaField branches
    }}
  </Field>
)}
```

…and replace with:

```tsx
{(() => {
  switch (filter) {
    case 'evaluationDateRange':
      return (
        <DateRangeField
          isModalOpen={isDateRangeModalOpen}
          onModalClose={() => setIsDateRangeModalOpen(false)}
          onClick={() => setIsDateRangeModalOpen(true)}
          filterName={filter}
          popoverTitle="Filter decisions by evaluation date"
          label={OPTIONAL_FILTER_FIELDS[filter].label}
          fromDateTimeKey="evaluationDateFrom"
          toDateTimeKey="evaluationDateTo"
        />
      );
    case 'businessId':
      return <BusinessIdFilter />;
    default:
      return (
        <Field
          name={filter}
          validate={OPTIONAL_FILTER_FIELDS[filter].validate}
        >
          {({input}) => {
            const field = OPTIONAL_FILTER_FIELDS[filter];

            if (field.type === 'text') {
              return (
                <TextInputField
                  {...input}
                  id={filter}
                  size="sm"
                  labelText={field.label}
                  placeholder={field.placeholder}
                  autoFocus
                />
              );
            }
            if (field.type === 'multiline') {
              return (
                <TextAreaField
                  {...input}
                  id={filter}
                  labelText={field.label}
                  placeholder={field.placeholder}
                  rows={field.rows}
                  autoFocus
                />
              );
            }
          }}
        </Field>
      );
  }
})()}
```

- [ ] **Step 4: Verify lint and types**

Run: `cd operate/client && npm run lint`
Expected: PASS.

- [ ] **Step 5: Smoke test in dev server**

Run: `cd operate/client && npm run start`

In the running app, open Decisions, add the Business ID optional filter, and run through the same operator-switch flow as Task 4.2 Step 5. Stop the server with Ctrl+C.

- [ ] **Step 6: Commit**

```bash
git add operate/client/src/App/Decisions/Filters/OptionalFiltersFormGroup.tsx
git commit -m "feat: wire Business ID operator filter into Decisions"
```

---

## Phase 6 — Tasklist: Business ID field in Advanced Filters

### Task 6.1: Create tasklist operator constants

**Files:**
- Create: `tasklist/client/src/common/tasks/filters/businessIdOperators.ts`

- [ ] **Step 1: Write the constants file**

```ts
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Operator labels and IDs for the Business ID filter in tasklist.
 * Local mirror of the Operate constants under
 * `operate/client/src/App/Processes/ListView/Filters/BusinessIdFilter/constants.ts`.
 *
 * TODO: Wire up to the API once Business ID filtering is implemented backend-side.
 */
export type BusinessIdFilterOperator =
  | 'equals'
  | 'notEqual'
  | 'contains'
  | 'exists'
  | 'doesNotExist';

export const BUSINESS_ID_FILTER_OPERATORS: Array<{
  id: BusinessIdFilterOperator;
  i18nKey: string;
  requiresValue: boolean;
}> = [
  {id: 'equals', i18nKey: 'filterOperatorEquals', requiresValue: true},
  {id: 'notEqual', i18nKey: 'filterOperatorNotEqual', requiresValue: true},
  {id: 'contains', i18nKey: 'filterOperatorContains', requiresValue: true},
  {id: 'exists', i18nKey: 'filterOperatorExists', requiresValue: false},
  {
    id: 'doesNotExist',
    i18nKey: 'filterOperatorDoesNotExist',
    requiresValue: false,
  },
];

export const DEFAULT_BUSINESS_ID_OPERATOR: BusinessIdFilterOperator = 'equals';
```

- [ ] **Step 2: Verify lint passes**

Run: `cd tasklist/client && npm run lint`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add tasklist/client/src/common/tasks/filters/businessIdOperators.ts
git commit -m "feat: add tasklist Business ID filter operator constants"
```

---

### Task 6.2: Add Business ID + operator fields to the tasklist custom filters schema

**Files:**
- Modify: `tasklist/client/src/common/tasks/filters/customFiltersSchema.ts`

- [ ] **Step 1: Import the operator type**

At the top of the file, add:

```ts
import {type BusinessIdFilterOperator} from './businessIdOperators';
```

- [ ] **Step 2: Add the two optional fields to `customFiltersSchema`**

Inside the `z.object({...})` (currently lines 12–67), add these two entries between `taskId: z.string().trim().optional(),` and `variables: ...`:

```ts
businessId: z.string().trim().optional(),
businessIdOperator: z
  .enum(['equals', 'notEqual', 'contains', 'exists', 'doesNotExist'])
  .optional(),
```

The duplicated literal list (rather than re-using `BusinessIdFilterOperator`) is intentional: `z.enum` requires a literal string-tuple at construction time. The TS-level `BusinessIdFilterOperator` import gives us the import path so we can verify the two stay in sync (one type-narrowing, one runtime).

- [ ] **Step 3: Add a small static alignment check**

At the bottom of the file (just before the `export`s), add:

```ts
// Compile-time guard: zod literal list stays aligned with BusinessIdFilterOperator.
type _ZodBusinessIdOperator = NonNullable<
  z.infer<typeof customFiltersSchema>['businessIdOperator']
>;
const _alignment: _ZodBusinessIdOperator = 'equals' satisfies BusinessIdFilterOperator;
void _alignment;
```

If the zod literal list ever drifts from the TS type, TypeScript fails compilation here. The `void` line keeps ESLint's no-unused-vars happy.

- [ ] **Step 4: Verify lint and types**

Run: `cd tasklist/client && npm run lint`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add tasklist/client/src/common/tasks/filters/customFiltersSchema.ts
git commit -m "feat: extend tasklist custom filters schema with Business ID + operator"
```

---

### Task 6.3: Add i18n keys (en, de, es, fr)

**Files:**
- Modify: `tasklist/client/src/common/i18n/locales/en.json`
- Modify: `tasklist/client/src/common/i18n/locales/de.json`
- Modify: `tasklist/client/src/common/i18n/locales/es.json`
- Modify: `tasklist/client/src/common/i18n/locales/fr.json`

- [ ] **Step 1: Read the existing en.json to find the right insertion point**

Run: `grep -n "customFiltersModalTaskIDLabel\|customFiltersModalTaskVariableLabel" tasklist/client/src/common/i18n/locales/en.json`

Use the line numbers returned to locate the existing `customFiltersModalTaskIDLabel` key — insert the new keys directly after it, alphabetically grouped with the rest of the `customFiltersModal*` family.

- [ ] **Step 2: Add the new keys to en.json**

Insert these seven keys after `customFiltersModalTaskIDLabel` (preserve trailing comma rules — the previous line gets a comma, the inserted block ends without one if it's the last in the section):

```json
"customFiltersModalBusinessIdLabel": "Business ID",
"customFiltersModalBusinessIdOperatorLabel": "Business ID condition",
"filterOperatorEquals": "equals",
"filterOperatorNotEqual": "not equal",
"filterOperatorContains": "contains",
"filterOperatorExists": "exists",
"filterOperatorDoesNotExist": "does not exist",
```

- [ ] **Step 3: Add the same keys to de.json with German translations**

```json
"customFiltersModalBusinessIdLabel": "Geschäfts-ID",
"customFiltersModalBusinessIdOperatorLabel": "Geschäfts-ID-Bedingung",
"filterOperatorEquals": "gleich",
"filterOperatorNotEqual": "ungleich",
"filterOperatorContains": "enthält",
"filterOperatorExists": "vorhanden",
"filterOperatorDoesNotExist": "nicht vorhanden",
```

- [ ] **Step 4: Add the same keys to es.json with Spanish translations**

```json
"customFiltersModalBusinessIdLabel": "ID de negocio",
"customFiltersModalBusinessIdOperatorLabel": "Condición del ID de negocio",
"filterOperatorEquals": "es igual a",
"filterOperatorNotEqual": "no es igual a",
"filterOperatorContains": "contiene",
"filterOperatorExists": "existe",
"filterOperatorDoesNotExist": "no existe",
```

- [ ] **Step 5: Add the same keys to fr.json with French translations**

```json
"customFiltersModalBusinessIdLabel": "ID métier",
"customFiltersModalBusinessIdOperatorLabel": "Condition de l'ID métier",
"filterOperatorEquals": "égal à",
"filterOperatorNotEqual": "différent de",
"filterOperatorContains": "contient",
"filterOperatorExists": "existe",
"filterOperatorDoesNotExist": "n'existe pas",
```

- [ ] **Step 6: Verify all four files parse as valid JSON**

Run: `node -e "['en','de','es','fr'].forEach(l => JSON.parse(require('fs').readFileSync('tasklist/client/src/common/i18n/locales/'+l+'.json','utf8')))"`
Expected: no output (silent success). Any parse error will print and exit non-zero.

- [ ] **Step 7: Commit**

```bash
git add tasklist/client/src/common/i18n/locales/en.json tasklist/client/src/common/i18n/locales/de.json tasklist/client/src/common/i18n/locales/es.json tasklist/client/src/common/i18n/locales/fr.json
git commit -m "feat: add i18n keys for tasklist Business ID filter and operators"
```

---

### Task 6.4: Add Business ID + operator fields to the Advanced Filters modal

**Files:**
- Modify: `tasklist/client/src/common/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal/FieldsModal.tsx`

- [ ] **Step 1: Add imports**

At the top of the file, add to the existing imports (Carbon `Dropdown` may need to be added to the `@carbon/react` import; check the existing import line and append `Dropdown` to it):

```ts
import {
  BUSINESS_ID_FILTER_OPERATORS,
  DEFAULT_BUSINESS_ID_OPERATOR,
  type BusinessIdFilterOperator,
} from 'common/tasks/filters/businessIdOperators';
```

(Verify by running `grep -n "@carbon/react" tasklist/client/src/common/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal/FieldsModal.tsx` whether `Dropdown` is already imported. If not, add it to the destructuring list.)

- [ ] **Step 2: Extend the `ADVANCED_FILTERS` constant**

Find the `ADVANCED_FILTERS` array (currently around line 53):

```ts
const ADVANCED_FILTERS: Array<keyof NamedCustomFilters> = [
  'dueDateFrom',
  'dueDateTo',
  'followUpDateFrom',
  'followUpDateTo',
  'taskId',
  'variables',
];
```

Append `'businessId'` and `'businessIdOperator'`:

```ts
const ADVANCED_FILTERS: Array<keyof NamedCustomFilters> = [
  'dueDateFrom',
  'dueDateTo',
  'followUpDateFrom',
  'followUpDateTo',
  'taskId',
  'businessId',
  'businessIdOperator',
  'variables',
];
```

- [ ] **Step 3: Add the new fields directly below the Task ID block**

Find the existing `<Field name="taskId">` block (currently lines 429–437):

```tsx
<Field name="taskId">
  {({input}) => (
    <TextInput
      {...input}
      id={input.name}
      labelText={t('customFiltersModalTaskIDLabel')}
    />
  )}
</Field>
```

Immediately **after** that closing `</Field>` and **before** the variables `<FieldArray ...>`, insert the Business ID block:

```tsx
<Field name="businessIdOperator">
  {({input: operatorInput}) => {
    const rawValue =
      typeof operatorInput.value === 'string'
        ? (operatorInput.value as BusinessIdFilterOperator)
        : undefined;
    const selectedId = rawValue ?? DEFAULT_BUSINESS_ID_OPERATOR;
    const selectedOperator =
      BUSINESS_ID_FILTER_OPERATORS.find((op) => op.id === selectedId) ??
      BUSINESS_ID_FILTER_OPERATORS[0];
    const isValueRequired = selectedOperator.requiresValue;

    return (
      <FormGroup
        legendText={t('customFiltersModalBusinessIdLabel')}
        // TODO: Wire up Business ID filtering once the API supports
        // $eq / $neq / $like / $exists for the businessId field.
      >
        <Dropdown<(typeof BUSINESS_ID_FILTER_OPERATORS)[number]>
          id="businessIdOperator"
          titleText={t('customFiltersModalBusinessIdOperatorLabel')}
          hideLabel
          label={t('customFiltersModalBusinessIdOperatorLabel')}
          size="md"
          items={BUSINESS_ID_FILTER_OPERATORS}
          itemToString={(item) => (item ? t(item.i18nKey) : '')}
          selectedItem={selectedOperator}
          onChange={({selectedItem}) => {
            operatorInput.onChange(
              selectedItem?.id ?? DEFAULT_BUSINESS_ID_OPERATOR,
            );
          }}
        />
        {isValueRequired ? (
          <Field name="businessId">
            {({input: valueInput}) => (
              <TextInput
                {...valueInput}
                id={valueInput.name}
                labelText={t('customFiltersModalBusinessIdLabel')}
                hideLabel
              />
            )}
          </Field>
        ) : null}
      </FormGroup>
    );
  }}
</Field>
```

A note on `FormGroup`: it's already imported in this file (used by date and variable groups). If it doesn't render visually well alongside the other field types here, swap to a plain `<div>` wrapper — visual polish is a follow-up.

- [ ] **Step 4: Verify lint and types**

Run: `cd tasklist/client && npm run lint`
Expected: PASS.

- [ ] **Step 5: Smoke test in dev server**

Run: `cd tasklist/client && npm run start`

In the running app, open the Custom Filters modal, toggle Advanced Filters on, and confirm:
1. Below the Task ID input, a Business ID block appears with the operator dropdown defaulting to `equals` and a value input.
2. Switching the dropdown to `exists` hides the value input. Switching back to `equals`/`contains`/`not equal` brings it back.
3. Form save/apply doesn't error. The two values are present in the saved filter (visible by re-opening the modal — though they don't yet affect which tasks are listed; that's the integration point flagged by the TODO comment).

Stop the server with Ctrl+C.

- [ ] **Step 6: Commit**

```bash
git add tasklist/client/src/common/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal/FieldsModal.tsx
git commit -m "feat: add Business ID field with operator support to tasklist Advanced Filters"
```

---

## Phase 7 — Repair existing broken tests, full lint/build

### Task 7.1: Run the Operate test suite and fix only structural breakage

**Files:**
- Modify (only if broken): `operate/client/src/App/Processes/ListView/Filters/tests/index.test.tsx`
- Modify (only if broken): `operate/client/src/App/Processes/ListView/Filters/tests/fieldinteractions.test.tsx`
- Modify (only if broken): `operate/client/src/App/Processes/ListView/Filters/tests/optionalFilters.test.tsx`
- Modify (only if broken): `operate/client/src/App/Processes/ListView/Filters/tests/validations.test.tsx`

- [ ] **Step 1: Run the four affected test files**

Run: `cd operate/client && npm run test -- src/App/Processes/ListView/Filters/tests`
Expected: most tests pass. Any failure that mentions `Business ID`, `businessId`, or the previous text-input role/testid for Business ID is in scope. **Any other failure is out of scope** (likely a flaky test or unrelated regression — investigate separately, do not fix here).

- [ ] **Step 2: For each Business-ID-related failure, update the test minimally**

Common patterns and their fixes:
- `getByLabelText('Business ID')` returning the old `<input>` but expecting a string match — change to `screen.getByTestId('business-id-value')` for the value input or `screen.getByTestId('business-id-operator')` for the operator dropdown.
- `userEvent.type(input, 'foo')` against the value input — works unchanged once the test gets a handle to the `business-id-value` input.
- Tests asserting that adding the optional filter gives focus to a single text field — relax the assertion or update to assert that the operator dropdown is visible and `equals` is selected.

**Do not** add new test cases for the operator dropdown or the contains/exists branches — that's explicitly out of scope per the spec (prototype, no new tests).

- [ ] **Step 3: Run all four test files together to confirm green**

Run: `cd operate/client && npm run test -- src/App/Processes/ListView/Filters/tests`
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add operate/client/src/App/Processes/ListView/Filters/tests
git commit -m "test: update existing Business ID filter tests for operator-aware structure"
```

(Skip this commit if no test files needed changes.)

---

### Task 7.2: Run the tasklist test suite and fix only structural breakage

**Files:**
- Modify (only if broken): files under `tasklist/client/src/common/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal/`

- [ ] **Step 1: Run the modal tests**

Run: `cd tasklist/client && npm run test -- src/common/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal`
Expected: all tests pass. The existing tests cover the advanced-filters toggle and the variables FieldArray; they should not reference `businessId` and should not break.

- [ ] **Step 2: If any test fails because of structural drift, repair minimally**

Same scope rules as Task 7.1 — do not add new test cases. If the schema-extension caused a test to break (e.g. snapshot mismatch), accept the new snapshot only after eyeballing the diff.

- [ ] **Step 3: Commit (only if any file changed)**

```bash
git add tasklist/client/src/common/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal
git commit -m "test: update tasklist custom-filters tests for Business ID schema additions"
```

---

### Task 7.3: Final lint and project build

**Files:** none

- [ ] **Step 1: Lint Operate**

Run: `cd operate/client && npm run lint`
Expected: PASS.

- [ ] **Step 2: Lint tasklist**

Run: `cd tasklist/client && npm run lint`
Expected: PASS.

- [ ] **Step 3: Build Operate**

Run: `cd operate/client && npm run build`
Expected: build succeeds. (If the project root has a unified build, that's the canonical check; this step is the local-fast path.)

- [ ] **Step 4: Build tasklist**

Run: `cd tasklist/client && npm run build`
Expected: build succeeds.

If any step fails, stop and report — do not paper over with `eslint-disable` or `@ts-ignore`.

---

## Out of scope (TODO captured in code)

- New Vitest tests for `BusinessIdFilter` and the new tasklist fields. Captured by `// TODO` comments at integration points and in `constants.ts`. Track as a follow-up before any non-prototype merge.
- Real API integration. The `buildBusinessIdFilterValue` helper in `constants.ts` produces the API-shaped object; the tasklist consumer pipeline ignores `businessId`/`businessIdOperator` until backend support lands.
- Lifting the Operate and tasklist operator constants into a shared module under `webapps-common/`. Keep duplicated for now to avoid prematurely entangling the two apps.
- Decisions Business ID semantics review. The filter is implemented for parity, but verify with the Decisions team that it's meaningful before promoting beyond the prototype.
