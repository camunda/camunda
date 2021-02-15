/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import update from 'immutability-helper';

import {formatters} from 'services';

import processDefaultData from './processDefaultData';

const {getRelativeValue} = formatters;

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  formatters: {
    getRelativeValue: jest.fn(),
    formatReportResult: jest.fn().mockReturnValue([
      {key: 'a', value: 1, label: 'a name'},
      {key: 'b', value: 2, label: 'b'},
      {key: 'c', value: 3, label: 'c'},
    ]),
    duration: (a) => 'Duration: ' + a,
  },
}));

const report = {
  reportType: 'process',
  combined: false,
  data: {
    groupBy: {
      value: {},
      type: '',
    },
    view: {properties: ['frequency']},
    configuration: {
      tableColumns: {
        includeNewVariables: true,
        includedColumns: [],
        excludedColumns: [],
      },
      hideRelativeValue: true,
      hideAbsoluteValue: false,
    },
    visualization: 'table',
  },
  result: {
    data: [],
    instanceCount: 5,
  },
};

const props = {
  mightFail: jest.fn().mockImplementation((a, b) => b(a)),
  error: '',
  formatter: (v) => v,
  report,
};

it('should display data for key-value pairs', async () => {
  expect(processDefaultData(props).body).toEqual([
    ['a name', 1],
    ['b', 2],
    ['c', 3],
  ]);
});

it('should format data according to the provided formatter', async () => {
  const newProps = {
    ...props,
    formatter: (v) => 2 * v,
  };
  expect(processDefaultData(newProps).body).toEqual([
    ['a name', 2],
    ['b', 4],
    ['c', 6],
  ]);
});

it('should format the label when the report is grouped by duration', () => {
  const newProps = {
    ...props,
    report: update(report, {data: {groupBy: {$set: {type: 'duration'}}}}),
  };
  expect(processDefaultData(newProps).body).toEqual([
    ['Duration: a name', 1],
    ['Duration: b', 2],
    ['Duration: c', 3],
  ]);
});

const newProps = {
  ...props,
  report: {
    ...report,
    data: {
      ...report.data,
      configuration: {
        hideAbsoluteValue: true,
        hideRelativeValue: false,
      },
    },
  },
};

it('should not include absolute values if if it is hidden in the configuration', () => {
  getRelativeValue.mockClear();
  getRelativeValue.mockReturnValue('12.3%');

  expect(processDefaultData(newProps).body).toEqual([
    ['a name', '12.3%'],
    ['b', '12.3%'],
    ['c', '12.3%'],
  ]);
});

it('should display the relative percentage for frequency views', () => {
  getRelativeValue.mockClear();
  getRelativeValue.mockReturnValue('12.3%');

  const tableData = processDefaultData(newProps);

  expect(getRelativeValue).toHaveBeenCalledWith(1, 5);
  expect(getRelativeValue).toHaveBeenCalledWith(2, 5);
  expect(getRelativeValue).toHaveBeenCalledWith(3, 5);

  expect(tableData.body[0][1]).toBe('12.3%');
});

it('should display the relative percentage for frequency views for DMN', () => {
  getRelativeValue.mockClear();
  getRelativeValue.mockReturnValue('12.3%');

  const dmnProps = {
    ...newProps,
    report: {
      ...newProps.report,
      result: {
        ...newProps.report.result,
        instanceCount: 18,
      },
    },
  };

  const dmnTableData = processDefaultData(dmnProps);

  expect(getRelativeValue).toHaveBeenCalledWith(1, 18);
  expect(getRelativeValue).toHaveBeenCalledWith(2, 18);
  expect(getRelativeValue).toHaveBeenCalledWith(3, 18);

  expect(dmnTableData.body[0][1]).toBe('12.3%');
});
