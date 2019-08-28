The component is used for the column headers in the instances list's table.

If the column supports sorting, a button with a title and onClick handler is returned.

```js
<ColumnHeader
  active={true}
  label="End Time"
  onSort={() => {}}
  sortKey="endDate"
  sorting={{sortBy: "workflowName"
  sortOrder: "desc"}}
/>
```

If the column has no sorting, only the text is returned.
In this case, it has 2 states, default and disabled.

```js
Active label <br />
<ColumnHeader label="Actions" /> <br /><br />
Disabled label, when the table list is empty <br />
<ColumnHeader disabled={true} label="Actions" />
```
