A single retry action item

```js
<ActionItems>
  <ActionItems.Item
    type={'UPDATE_RETRIES'}
    onClick={() => console.log('foo')}
    title="Retry instance 1"
  />
</ActionItems>
```

A single cancel action item

```js
<ActionItems>
  <ActionItems.Item type={'CANCEL'} onClick={() => console.log('foo')} title="Cancel instance 1"/>
</ActionItems>
```

Multiple action items

```js
<ActionItems>
  <ActionItems.Item
    type="UPDATE_RETRIES"
    onClick={() => console.log('foobar')}
    title="Retry instance 1"
  />
  <ActionItems.Item type="CANCEL" onClick={() => console.log('foobar')} title="Cancel instance 1"/>
  <ActionItems.Item
    type="UPDATE_RETRIES"
    onClick={() => console.log('foobar')}
    title="Retry instance 1"
  />
</ActionItems>
```
