/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
    frequency: (a) => a,
  },
}));

const report = {
  reportType: 'process',
  combined: false,
  data: {
    groupBy: {type: 'startDate', value: {unit: 'automatic'}},
    view: {entity: 'processInstance', properties: ['frequency']},
    configuration: {
      tableColumns: {
        includeNewVariables: true,
        includedColumns: [],
        excludedColumns: [],
        columnOrder: [],
      },
      hideRelativeValue: true,
      hideAbsoluteValue: false,
    },
    visualization: 'table',
  },
  result: {
    measures: [{property: 'frequency', data: []}],
    instanceCount: 5,
  },
};

const props = {
  report,
};

it('should display data for key-value pairs', async () => {
  expect(processDefaultData(props).body).toEqual([
    ['a name', 1],
    ['b', 2],
    ['c', 3],
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
        tableColumns: {
          columnOrder: [],
        },
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

it('should display multi-measure reports', () => {
  const table = processDefaultData({
    report: update(report, {
      data: {view: {properties: {$set: ['frequency', 'duration']}}},
      result: {
        measures: {
          $set: [
            {property: 'frequency', data: []},
            {property: 'duration', aggregationType: {type: 'avg', value: null}, data: []},
          ],
        },
      },
    }),
  });

  expect(table).toMatchSnapshot();
});
