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
