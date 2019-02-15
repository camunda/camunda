import processRawData from './processRawData';

const data = [
  {
    processInstanceId: 'foo',
    prop2: 'bar',
    variables: {
      var1: 12,
      var2: null
    }
  },
  {
    processInstanceId: 'xyz',
    prop2: 'abc',
    variables: {
      var1: null,
      var2: true
    }
  }
];

it('should transform data to table compatible format', () => {
  expect(processRawData({data})).toEqual({
    head: ['Process Instance Id', 'Prop2', {label: 'Variables', columns: ['var1', 'var2']}],
    body: [['foo', 'bar', '12', ''], ['xyz', 'abc', '', 'true']]
  });
});

it('should not include columns that are hidden', () => {
  expect(processRawData({data, excludedColumns: ['prop2']})).toEqual({
    head: ['Process Instance Id', {label: 'Variables', columns: ['var1', 'var2']}],
    body: [['foo', '12', ''], ['xyz', '', 'true']]
  });
});

it('should exclude variable columns using the var__ prefix', () => {
  expect(processRawData({data, excludedColumns: ['var__var1']})).toEqual({
    head: ['Process Instance Id', 'Prop2', {label: 'Variables', columns: ['var2']}],
    body: [['foo', 'bar', ''], ['xyz', 'abc', 'true']]
  });
});

it('should apply column order', () => {
  expect(
    processRawData({
      data,
      columnOrder: {
        instanceProps: ['Prop2', 'Process Instance Id'],
        variables: ['var1', 'var2']
      }
    })
  ).toEqual({
    head: ['Prop2', 'Process Instance Id', {label: 'Variables', columns: ['var1', 'var2']}],
    body: [['bar', 'foo', '12', ''], ['abc', 'xyz', '', 'true']]
  });
});

it('should prepend columns without specified column position', () => {
  expect(
    processRawData({
      data,
      columnOrder: {instanceProps: ['Process Instance Id'], variables: ['var1']}
    })
  ).toEqual({
    head: ['Prop2', 'Process Instance Id', {label: 'Variables', columns: ['var2', 'var1']}],
    body: [['bar', 'foo', '', '12'], ['abc', 'xyz', 'true', '']]
  });
});

it('should sort and hide simulateously', () => {
  expect(
    processRawData({
      data,
      excludedColumns: ['prop2'],
      columnOrder: {
        instanceProps: ['Prop2', 'Process Instance Id'],
        variables: ['var2', 'var1']
      }
    })
  ).toEqual({
    head: ['Process Instance Id', {label: 'Variables', columns: ['var2', 'var1']}],
    body: [['foo', '', '12'], ['xyz', 'true', '']]
  });
});

it('should make the process instance id a link', () => {
  const cell = processRawData({
    data: [{processInstanceId: '123', engineName: '1', variables: {}}],
    endpoints: {1: {endpoint: 'http://camunda.com', engineName: 'a'}}
  }).body[0][0];

  expect(cell.type).toBe('a');
  expect(cell.props.href).toBe('http://camunda.com/app/cockpit/a/#/process-instance/123');
});

it('should not make the process instance id a link if no endpoint is specified', () => {
  const cell = processRawData({data: [{processInstanceId: '123', engineName: '1', variables: {}}]})
    .body[0][0];

  expect(cell).toBe('123');
});

it('should make the decision instance id a link', () => {
  const cell = processRawData({
    reportType: 'decision',
    data: [
      {
        decisionInstanceId: '123',
        engineName: '1',
        inputVariables: {},
        outputVariables: {}
      }
    ],
    endpoints: {1: {endpoint: 'http://camunda.com', engineName: 'a'}}
  }).body[0][0];

  expect(cell.type).toBe('a');
  expect(cell.props.href).toBe('http://camunda.com/app/cockpit/a/#/decision-instance/123');
});

it('should show no data message when all column are excluded', () => {
  expect(
    processRawData({
      data,
      excludedColumns: ['processInstanceId', 'prop2', 'var__var1', 'var__var2']
    })
  ).toEqual({
    head: ['No Data'],
    body: [['You need to enable at least one table column']]
  });
});

it('should show no data message when all column are excluded for decision tables', () => {
  const data = [
    {
      decisionInstanceId: 'foo',
      prop2: 'bar',
      inputVariables: {
        var1: {id: 'var1', value: 12, name: 'Var 1'}
      },
      outputVariables: {
        result: {id: 'result', values: [1], name: 'Result'}
      }
    }
  ];
  expect(
    processRawData({
      data,
      excludedColumns: ['decisionInstanceId', 'prop2', 'inp__var1', 'out__result'],
      reportType: 'decision'
    })
  ).toEqual({
    head: ['No Data'],
    body: [['You need to enable at least one table column']]
  });
});

it('should work for decision tables', () => {
  const data = [
    {
      decisionInstanceId: 'foo',
      prop2: 'bar',
      inputVariables: {
        var1: {id: 'var1', value: 12, name: 'Var 1'},
        var2: {id: 'var2', value: null, name: 'Var 2'}
      },
      outputVariables: {
        result: {id: 'result', values: [1], name: 'Result'}
      }
    },
    {
      decisionInstanceId: 'xyz',
      prop2: 'abc',
      inputVariables: {
        var1: {id: 'var1', value: null, name: 'Var 1'},
        var2: {id: 'var2', value: true, name: 'Var 2'}
      },
      outputVariables: {
        result: {id: 'result', values: [8], name: 'Result'}
      }
    }
  ];

  expect(processRawData({data, reportType: 'decision'})).toEqual({
    head: [
      'Decision Instance Id',
      'Prop2',
      {
        label: 'Input Variables',
        columns: [{id: 'var1', label: 'Var 1'}, {id: 'var2', label: 'Var 2'}]
      },
      {label: 'Output Variables', columns: [{id: 'result', label: 'Result'}]}
    ],
    body: [['foo', 'bar', '12', '', '1'], ['xyz', 'abc', '', 'true', '8']]
  });
});
