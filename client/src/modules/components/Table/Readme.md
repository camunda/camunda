Table Example

```js
const headers = {foo: 'foo', bar: 'bar'};

const data = [
  {
    foo: 'foo1',
    bar: 'bar1'
  },
    {
      foo: 'foo2',
    bar: 'bar2'
  }
].map(row => ({ data: {...row}, view: {...row}}));

const config = {
  isSortable: {foo: false, bar: false}
};

<Table headers={headers} data={data} config={config} />;
```
