Example:

```js
<div style={{marginBottom: '5px'}}>
  <StateIcon instance={{
    state: 'ACTIVE',
    incidents: []
  }}/> {' '}  active
</div>
<div style={{marginBottom: '5px'}}>
  <StateIcon instance={{
    state: 'INCIDENT',
    incidents: [{ state: 'ACTIVE'}]
  }}/> {' '}  incident
</div>
<div style={{marginBottom: '5px'}}>
  <StateIcon instance={{
    state: 'COMPLETED',
    incidents: []
  }}/> {' '}  completed
</div>
<div style={{marginBottom: '5px'}}>
  <StateIcon instance={{
    state: 'CANCELED',
    incidents: []
  }}/> {' '}  canceled
</div>
<div style={{marginBottom: '5px'}}>
  <StateIcon instance={{
    state: 'SOME_NEW_STATE',
    incidents: []
  }}/> {' '}  backup icon if state type doesn't match available icon
</div>
```
