/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import processDecisionRawData from './processDecisionRawData';
import {NoDataNotice} from 'components';
import React from 'react';

const data = {
  configuration: {
    tableColumns: {
      includeNewVariables: false,
      includedColumns: [
        'decisionInstanceId',
        'decisionDefinitionId',
        'processInstanceId',
        'engineName',
        'input:var1',
        'input:var2',
        'output:result',
      ],
      excludedColumns: [],
      columnOrder: [],
    },
  },
};

it('should display decision and process instance ids as links', () => {
  const cell = processDecisionRawData(
    {
      report: {
        reportType: 'decision',
        result: {
          data: [
            {
              decisionInstanceId: '123',
              processInstanceId: '456',
              decisionDefinitionId: 'bar',
              engineName: 'a',
              inputVariables: {},
              outputVariables: {},
            },
          ],
        },
        data,
      },
    },
    {a: {endpoint: 'http://camunda.com', engineName: 'a'}}
  ).body[0];

  expect(cell[1].type).toBe('a');
  expect(cell[1].props.href).toBe('http://camunda.com/app/cockpit/a/#/decision-instance/123');
  expect(cell[2].props.href).toBe('http://camunda.com/app/cockpit/a/#/process-instance/456');
});

it('should return correct table props for decision tables', () => {
  const result = {
    data: [
      {
        decisionInstanceId: 'foo',
        processInstanceId: '456',
        decisionDefinitionId: 'bar',
        engineName: '1',
        inputVariables: {
          var1: {id: 'var1', value: 12, name: 'Var 1'},
          var2: {id: 'var2', value: null, name: 'Var 2'},
        },
        outputVariables: {
          result: {id: 'result', values: [1], name: 'Result'},
        },
      },
      {
        decisionInstanceId: 'xyz',
        processInstanceId: '456',
        decisionDefinitionId: 'abc',
        engineName: '1',
        inputVariables: {
          var1: {id: 'var1', value: null, name: 'Var 1'},
          var2: {id: 'var2', value: true, name: 'Var 2'},
        },
        outputVariables: {
          result: {id: 'result', values: [8], name: 'Result'},
        },
      },
    ],
  };

  expect(
    processDecisionRawData({report: {result, reportType: 'decision', data}})
  ).toMatchSnapshot();
});

it('should show no data message when all column are excluded for decision tables', () => {
  const result = {
    data: [
      {
        decisionInstanceId: 'foo',
        decisionDefinitionId: 'bar',
        inputVariables: {
          var1: {id: 'var1', value: 12, name: 'Var 1'},
        },
        outputVariables: {
          result: {id: 'result', values: [1], name: 'Result'},
        },
      },
    ],
  };
  expect(
    processDecisionRawData({
      report: {
        reportType: 'decision',
        result,
        data: {
          configuration: {
            tableColumns: {
              columnOrder: [],
              includeNewVariables: true,
              includedColumns: [],
              excludedColumns: [
                'decisionDefinitionKey',
                'decisionDefinitionId',
                'decisionInstanceId',
                'processInstanceId',
                'evaluationDateTime',
                'engineName',
                'tenantId',
                'input:var1',
                'input:var2',
                'output:result',
              ],
            },
          },
        },
      },
    })
  ).toEqual({
    body: [],
    head: [],
    noData: <NoDataNotice type="info">You need to enable at least one table column</NoDataNotice>,
  });
});

it('should only show instance table column if result data is empty', () => {
  expect(
    processDecisionRawData({report: {data, reportType: 'decision', result: {data: []}}})
  ).toMatchSnapshot();
});
