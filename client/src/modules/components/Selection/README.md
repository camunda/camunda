A closed Selection

```js
<Selection
  isOpen={false}
  selectionId={'123'}
  instances={
    new Map([
      [
        '123456',
        {
          activities: [],
          bpmnProcessId: 'someKey',
          endDate: null,
          id: '123456',
          incidents: [],
          operations: [],
          sequenceFlows: [],
          startDate: '2018-06-21',
          state: 'ACTIVE',
          workflowId: '2',
          workflowName: 'someWorkflowName',
          workflowVersion: 1
        }
      ]
    ])
  }
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
  instances={
    new Map([
      [
        '123456',
        {
          activities: [],
          bpmnProcessId: 'someKey',
          endDate: null,
          id: '123456',
          incidents: [],
          operations: [],
          sequenceFlows: [],
          startDate: '2018-06-21',
          state: 'ACTIVE',
          workflowId: '2',
          workflowName: 'someWorkflowName',
          workflowVersion: 1
        }
      ]
    ])
  }
  instanceCount={'1'}
  onDelete={() => {}}
  onRetry={() => {}}
  onToggle={() => {}}
/>
```
