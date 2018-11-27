Dropdown example with default 'bottom' placement:

```js
<div style={{display: 'inline-block'}}>
  <Dropdown label="Click me">
    <Dropdown.Option label='Option 1' onClick={() => alert('clicked 1')} />
    <Dropdown.Option label='Option 2' onClick={() => alert('clicked 2')} />
    <Dropdown.Option label='Option 3' onClick={() => alert('clicked 3')} />
  </Dropdown>
</div>
```

Dropdown example with 'top' placement:

```js
<div style={{height: '200px', display: 'flex', flexDirection: 'column-reverse'}}>
    <Dropdown placement="top" label="Click me">
      <Dropdown.Option label='Option 1' onClick={() => alert('clicked 1')} />
      <Dropdown.Option label='Option 2' onClick={() => alert('clicked 2')} />
      <Dropdown.Option label='Option 3' onClick={() => alert('clicked 3')} />
    </Dropdown>
</div>
```
