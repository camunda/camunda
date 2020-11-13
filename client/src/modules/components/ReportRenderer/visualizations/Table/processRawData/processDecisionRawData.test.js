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
      includeNewVariables: true,
      includedColumns: [],
      excludedColumns: [],
    },
  },
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
              outputVariables: {},
            },
          ],
        },
        data,
      },
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
        decisionDefinitionId: 'bar',
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
        decisionDefinitionId: 'abc',
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
              includeNewVariables: true,
              includedColumns: [],
              excludedColumns: [
                'decisionInstanceId',
                'decisionDefinitionId',
                'input:var1',
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
    noData: <NoDataNotice>You need to enable at least one table column</NoDataNotice>,
  });
});

it('should not crash for empty results', () => {
  expect(
    processDecisionRawData({report: {data, reportType: 'decision', result: {data: []}}})
  ).toEqual({
    body: [],
    head: [],
  });
});
