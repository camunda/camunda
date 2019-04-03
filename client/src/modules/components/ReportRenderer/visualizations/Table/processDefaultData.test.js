/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import processDefaultData from './processDefaultData';

import {getRelativeValue} from '../service';

jest.mock('../service', () => ({
  getRelativeValue: jest.fn()
}));

const report = {
  reportType: 'process',
  combined: false,
  data: {
    groupBy: {
      value: {},
      type: ''
    },
    view: {property: 'frequency'},
    configuration: {
      excludedColumns: [],
      hideRelativeValue: true,
      hideAbsoluteValue: false
    },
    visualization: 'table'
  },
  result: {data: {a: 1, b: 2, c: 3}, processInstanceCount: 5}
};

const props = {
  mightFail: jest.fn().mockImplementation((a, b) => b(a)),
  error: '',
  flowNodeNames: {
    a: 'a'
  },
  formatter: v => v,
  report
};

it('should display data for key-value pairs', async () => {
  expect(processDefaultData(props).body).toEqual([['a', 1], ['b', 2], ['c', 3]]);
});

it('should format data according to the provided formatter', async () => {
  const newProps = {
    ...props,
    formatter: v => 2 * v
  };
  expect(processDefaultData(newProps).body).toEqual([['a', 2], ['b', 4], ['c', 6]]);
});

const newProps = {
  ...props,
  report: {
    ...report,
    data: {
      ...report.data,
      configuration: {
        hideAbsoluteValue: true,
        hideRelativeValue: false
      }
    }
  }
};

it('should not include absolute values if if it is hidden in the configuration', () => {
  getRelativeValue.mockClear();
  getRelativeValue.mockReturnValue('12.3%');

  expect(processDefaultData(newProps).body).toEqual([
    ['a', '12.3%'],
    ['b', '12.3%'],
    ['c', '12.3%']
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
        decisionInstanceCount: 18,
        processInstanceCount: undefined
      }
    }
  };

  const dmnTableData = processDefaultData(dmnProps);

  expect(getRelativeValue).toHaveBeenCalledWith(1, 18);
  expect(getRelativeValue).toHaveBeenCalledWith(2, 18);
  expect(getRelativeValue).toHaveBeenCalledWith(3, 18);

  expect(dmnTableData.body[0][1]).toBe('12.3%');
});
