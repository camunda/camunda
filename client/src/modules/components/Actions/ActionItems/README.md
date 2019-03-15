A single retry action item

```js
<ActionItems>
  <ActionItems.Item
    type={'RESOLVE_INCIDENT'}
    onClick={() => console.log('foo')}
    title="Retry Instance 1"
  />
</ActionItems>
```

A single cancel action item

```js
<ActionItems>
  <ActionItems.Item type={'CANCEL_WORKFLOW_INSTANCE'} onClick={() => console.log('foo')} title="Cancel Instance 1"/>
</ActionItems>
```

Multiple action items

```js
<ActionItems>
  <ActionItems.Item
    type="RESOLVE_INCIDENT"
    onClick={() => console.log('foobar')}
    title="Retry Instance 1"
  />
  <ActionItems.Item type="CANCEL_WORKFLOW_INSTANCE" onClick={() => console.log('foobar')} title="Cancel Instance 1"/>
  <ActionItems.Item
    type="RESOLVE_INCIDENT"
    onClick={() => console.log('foobar')}
    title="Retry Instance 1"
  />
</ActionItems>
```
