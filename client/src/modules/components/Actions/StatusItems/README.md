A single retry status item

```js
<StatusItems>
  <StatusItems.Item type={'UPDATE_RETRIES'} />
</StatusItems>
```

A single cancel status item

```js
<StatusItems>
  <StatusItems.Item type={'CANCEL'} />
</StatusItems>
```

Multiple status items

```js
<StatusItems>
  <StatusItems.Item type="UPDATE_RETRIES" />
  <StatusItems.Item type="CANCEL" />
  <StatusItems.Item type="UPDATE_RETRIES" />
</StatusItems>
```
