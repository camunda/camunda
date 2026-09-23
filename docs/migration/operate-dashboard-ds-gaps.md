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

### Gap: no per-row override for the expansion toggle

DS `DataTable`'s `expansion` prop renders an expand/collapse toggle for every row unconditionally —
there is no public `getRowCanExpand`-style override (confirmed against the installed package's
`data-table.js`). The Carbon original hid the toggle entirely for rows with nothing to expand.
Current code doesn't use the `expansion` prop at all; it composes its own toggle inside the single
content column instead, showing it only when a row has `expandedContents`.

Needs design review: should DS `DataTable`'s `expansion` prop gain a per-row override, or is a
composed-in-column toggle (as done here) the intended pattern for lists like this one?
