A closed Selection

```js
<Selection
  isOpen={false}
  selectionId={'123'}
  instances={[{id: '123456', workflowId: '2'}]}
  instanceCount={'1'}
  onRetry={() => {}}
  onToggle={() => {}}
  onDelete={() => {}}
/>
```

A open Selection

```js
<Selection
  isOpen={true}
  selectionId={'123'}
  instances={[{id: '123456', workflowId: '2'}]}
  instanceCount={'1'}
  onDelete={() => {}}
  onRetry={() => {}}
  onToggle={() => {}}
/>
```
