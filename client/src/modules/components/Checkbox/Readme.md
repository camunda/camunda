Checkbox States

```js
<Checkbox isChecked={false} onChange={({isChecked}) => {console.log('is checked', isChecked)}} />
<Checkbox isChecked={true} onChange={() => {const foo = 'foo'}} />
<Checkbox
  isIndeterminate={true}
  isChecked={true}
  onChange={() => {const foo = 'foo'}}
/>
```

Checkbox with Label

```js
<Checkbox
  label="Running Instances"
  isChecked={true}
  onChange={() => {
    const foo = 'foo';
  }}
/>
```

Checkbox of type 'selection'

```js
<Checkbox
  isChecked={true}
  onChange={() => {
    const foo = 'foo';
  }}
  type="selection"
/>
```

Checkbox with additional standard attributes.

Beside the specified attributes the checkbox also supports all standard attributes.

```js
<Checkbox
  isChecked={true}
  onChange={() => {
    const foo = 'foo';
  }}
  name={'foobar'}
  autofocus={true}
/>
```
