Table Example

```js
const data = [
  {
    processDefinition: 'Process Definition Name',
    id: '123456',
    custom: <button>Yay</button>
  },
  {
    processDefinition: 'Process Definition Name',
    id: '123456',
    custom: <button>Yay</button>
  },
  {
    processDefinition: 'Process Definition Name',
    id: '123456',
    custom: <button>Yay</button>
  }
];

const config = {
  headerLabels: {
    processDefinition: 'Process Definition',
    id: 'Process ID',
    custom: <span style={{color: 'red'}}>Custom Markup!!</span>
  },
  order: ['id', 'processDefinition', 'custom']
};
<Table data={data} config={config} />;
```
