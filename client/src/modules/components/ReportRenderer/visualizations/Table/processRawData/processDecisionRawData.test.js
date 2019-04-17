/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import processDecisionRawData from './processDecisionRawData';

const data = {
  configuration: {
    excludedColumns: []
  }
};

it('should make the decision instance id a link', () => {
  const cell = processDecisionRawData(
    {
      report: {
        reportType: 'decision',
        result: {
          data: [
            {
              decisionInstanceId: '123',
              engineName: '1',
              inputVariables: {},
              outputVariables: {}
            }
          ]
        },
        data
      }
    },
    {1: {endpoint: 'http://camunda.com', engineName: 'a'}}
  ).body[0][0];

  expect(cell.type).toBe('a');
  expect(cell.props.href).toBe('http://camunda.com/app/cockpit/a/#/decision-instance/123');
});

it('should return correct table props for decision tables', () => {
  const result = {
    data: [
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
    ]
  };

  expect(processDecisionRawData({report: {result, reportType: 'decision', data}})).toEqual({
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

it('should show no data message when all column are excluded for decision tables', () => {
  const result = {
    data: [
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
    ]
  };
  expect(
    processDecisionRawData({
      report: {
        reportType: 'decision',
        result,
        data: {
          configuration: {
            excludedColumns: ['decisionInstanceId', 'prop2', 'inp__var1', 'out__result']
          }
        }
      }
    })
  ).toEqual({
    head: ['No Data'],
    body: [['You need to enable at least one table column']]
  });
});
