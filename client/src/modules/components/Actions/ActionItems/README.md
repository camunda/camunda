A single retry action item

```js
<ActionItems>
  <ActionItems.Item
    type={'UPDATE_RETRIES'}
    onClick={() => console.log('foo')}
  />
</ActionItems>
```

A single cancel action item

```js
<ActionItems>
  <ActionItems.Item type={'CANCEL'} onClick={() => console.log('foo')} />
</ActionItems>
```

Multiple action items

```js
<ActionItems>
  <ActionItems.Item
    type="UPDATE_RETRIES"
    onClick={() => console.log('foobar')}
  />
  <ActionItems.Item type="CANCEL" onClick={() => console.log('foobar')} />
  <ActionItems.Item
    type="UPDATE_RETRIES"
    onClick={() => console.log('foobar')}
  />
</ActionItems>
```
