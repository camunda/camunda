/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import processRawData from './processRawData';

jest.mock('services', () => ({
  formatters: {
    formatReportResult: (data, result) => result,
    convertCamelToSpaces: v => v
  }
}));

jest.mock('./service', () => ({
  sortColumns: (head, body) => ({sortedHead: head, sortedBody: body})
}));

const data = {
  configuration: {
    excludedColumns: []
  }
};

const result = {
  data: [
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
  ]
};

it('should transform data to table compatible format', () => {
  expect(processRawData({report: {data, result}})).toEqual({
    head: ['processInstanceId', 'prop2', {label: 'Variables', columns: ['var1', 'var2']}],
    body: [['foo', 'bar', '12', ''], ['xyz', 'abc', '', 'true']]
  });
});

it('should not include columns that are hidden', () => {
  const data = {
    configuration: {
      excludedColumns: ['prop2']
    }
  };
  expect(processRawData({report: {data, result}})).toEqual({
    head: ['processInstanceId', {label: 'Variables', columns: ['var1', 'var2']}],
    body: [['foo', '12', ''], ['xyz', '', 'true']]
  });
});

it('should exclude variable columns using the var__ prefix', () => {
  const data = {
    configuration: {
      excludedColumns: ['var__var1']
    }
  };
  expect(processRawData({report: {data, result}})).toEqual({
    head: ['processInstanceId', 'prop2', {label: 'Variables', columns: ['var2']}],
    body: [['foo', 'bar', ''], ['xyz', 'abc', 'true']]
  });
});

it('should make the processInstanceId a link', () => {
  const cell = processRawData(
    {
      report: {
        result: {data: [{processInstanceId: '123', engineName: '1', variables: {}}]},
        data
      }
    },
    {1: {endpoint: 'http://camunda.com', engineName: 'a'}}
  ).body[0][0];

  expect(cell.type).toBe('a');
  expect(cell.props.href).toBe('http://camunda.com/app/cockpit/a/#/process-instance/123');
});

it('should not make the processInstanceId a link if no endpoint is specified', () => {
  const cell = processRawData({
    report: {
      result: {data: [{processInstanceId: '123', engineName: '1', variables: {}}]},
      data
    }
  }).body[0][0];

  expect(cell).toBe('123');
});

it('should show no data message when all column are excluded', () => {
  const data = {
    configuration: {
      excludedColumns: ['processInstanceId', 'prop2', 'var__var1', 'var__var2']
    }
  };
  expect(processRawData({report: {data, result}})).toEqual({
    head: ['No Data'],
    body: [['You need to enable at least one table column']]
  });
});
