A single retry status item

```js
<StatusItems>
  <StatusItems.Item type={'UPDATE_JOB_RETRIES'} />
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
  <StatusItems.Item type="UPDATE_JOB_RETRIES" />
  <StatusItems.Item type="CANCEL_WORKFLOW_INSTANCE" />
  <StatusItems.Item type="UPDATE_JOB_RETRIES" />
</StatusItems>
```
