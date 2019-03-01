A single retry status item

```js
<StatusItems>
  <StatusItems.Item type={'RESOLVE_INCIDENT'} />
</StatusItems>
```

A single cancel status item

```js
<StatusItems>
  <StatusItems.Item type={'CANCEL_WORKFLOW_INSTANCE'} />
</StatusItems>
```

Multiple status items

```js
<StatusItems>
  <StatusItems.Item type="RESOLVE_INCIDENT" />
  <StatusItems.Item type="CANCEL_WORKFLOW_INSTANCE" />
  <StatusItems.Item type="RESOLVE_INCIDENT" />
</StatusItems>
```
