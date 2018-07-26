Dropdown example with default 'bottom' placement:

```js
<Dropdown label="Click me">
  <Dropdown.Option onClick={() => alert('clicked 1')}>Option 1</Dropdown.Option>
  <Dropdown.Option onClick={() => alert('clicked 2')}>Option 2</Dropdown.Option>
  <Dropdown.Option onClick={() => alert('clicked 3')}>Option 3</Dropdown.Option>
</Dropdown>
```

Dropdown example with 'top' placement:

```js
<Dropdown placement="top" label="Click me">
  <Dropdown.Option onClick={() => alert('clicked 1')}>Option 1</Dropdown.Option>
  <Dropdown.Option onClick={() => alert('clicked 2')}>Option 2</Dropdown.Option>
  <Dropdown.Option onClick={() => alert('clicked 3')}>Option 3</Dropdown.Option>
</Dropdown>
```
