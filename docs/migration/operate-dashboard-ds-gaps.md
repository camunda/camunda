# Operate Dashboard DS migration — visual gaps for design review

Tracks DS-imposed visual/behavioral gaps found while migrating the Operate Dashboard to the
Camunda Design System (parent: [#63416](https://github.com/camunda/camunda/issues/63416)), so they
reach design review instead of being silently worked around in application code.

## `ExpandableList` (issue [#63421](https://github.com/camunda/camunda/issues/63421))

Component: `webapp/client/apps/orchestration-cluster-webapp/src/operate/pages/Dashboard/shadcn.components/ExpandableList.tsx`

### Gap: `DataTable` always renders a header row

DS `DataTable` renders a header row whenever it has columns, with no prop to suppress it. The
Carbon original had no header row for this single-content-column list. Current code passes a
`<span className="sr-only">` as the column header so the row is visually a zero-height,
non-interactive strip rather than removed outright.

Needs design review: is an always-present (even if visually empty) header row acceptable for this
list shape, or should DS `DataTable` gain a way to omit it?

### Gap: no per-row override for the expansion toggle — resolved, adopted native `expansion`

DS `DataTable`'s `expansion` prop renders an expand/collapse toggle for every row unconditionally —
there is no public `getRowCanExpand`-style override (confirmed against the installed package's
`data-table.js`). The Carbon original hid the toggle entirely for rows with nothing to expand.

Decision: switched to the native `expansion` prop (matching the DS Storybook expansion pattern:
https://camunda.github.io/design-system/?path=/story/ui-datatable--expansion) instead of the
composed-in-column toggle. Rows with no `expandedContents` entry now show a toggle that expands
into nothing — an accepted, intentional regression from Carbon parity, traded for matching the DS
component's own expansion pattern out of the box.

Follow-on: the pagination loading skeletons are kept as siblings around `DataTable` rather than
synthetic table rows, specifically so they don't pick up this same always-on toggle.

Still open: should DS `DataTable`'s `expansion` prop gain a per-row override so consumers aren't
forced to choose between Carbon-parity behavior and the native pattern?

### Open design question: the row shape itself

The Carbon original gives each row a single opaque content cell, into which the name, the counts
and the ratio bar are all composed together. Ported literally onto DS `DataTable`, that makes the
table a layout container rather than a table: nothing is sortable per column, the counts are not
addressable as data, and the sr-only header row above is a direct consequence (see the first gap).

Three candidate row shapes are in review:

| Variant              | Row shape                                                       | Trade-off                                                                                       |
| -------------------- | --------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `composed`           | One content cell — name, counts and ratio bar composed together | Carbon parity. Keeps the header-row gap and forfeits per-column sorting.                        |
| `structured-columns` | Real `Name` / `Active` / `Incidents` columns, counts as badges  | Makes it an actual table — sortable, real headers. Loses the at-a-glance ratio the bar conveys. |
| `badge-row`          | Name plus discrete coloured count badges, no ratio bar          | Most compact; severity read from colour rather than by judging a bar's proportion.              |

This is tracked as a component axis, not a branch: `ExpandableList` takes a `variant` prop resolved
from `ExpandableList.variants.ts`, so the decision is a one-line change to
`DEFAULT_EXPANDABLE_LIST_VARIANT` and no consumer call site changes. Candidates and a
`/operate-preview/expandable-list-demo?variant=…` switcher live on the `operate-ds-expandable-variants`
branch, which is opened as a draft PR for review and closed — never merged — once a shape is chosen.

Needs design review: which row shape should Operate's list tiles adopt? The answer applies beyond the
Dashboard, since InstancesByProcess and IncidentsByError both render through this component.

Noted while prototyping `structured-columns`: it modelled drill-down versions as real sibling rows
inserted into `data` rather than through `DataTable`'s `expansion` prop, which reads as one
continuous shaded band across the three columns. The shipped variants all use the native
`expansion` prop instead, to keep one expansion behaviour across candidates. If `structured-columns`
wins, whether drill-downs should become sibling rows is a follow-up question.
