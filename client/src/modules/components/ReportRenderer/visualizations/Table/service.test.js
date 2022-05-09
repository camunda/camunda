/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  sortColumns,
  getFormattedLabels,
  getBodyRows,
  getCombinedTableProps,
  isVisibleColumn,
} from './service';

jest.mock('request', () => ({
  get: jest.fn(),
}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    reportConfig: {
      process: {
        view: [
          {
            matcher: () => true,
            label: () => 'Flow Node',
          },
        ],
        group: [
          {
            matcher: () => true,
            label: () => 'foo',
          },
        ],
      },
    },
  };
});

const unitedResults = [
  [
    {
      property: 'frequency',
      data: [
        {key: 'a', value: 1},
        {key: 'b', value: 2},
      ],
    },
  ],
  [
    {
      property: 'frequency',
      data: [
        {key: 'a', value: ''},
        {key: 'b', value: 0},
      ],
    },
  ],
];

it('should apply flow node names to the body rows', () => {
  expect(
    getBodyRows({
      unitedResults,
      allKeys: ['a', 'b'],
      formatter: (v) => v,
      displayRelativeValue: false,
      instanceCount: [100, 100],
      displayAbsoluteValue: true,
      flowNodeNames: {
        a: 'Flownode A',
        b: 'Flownode B',
      },
      groupedByDuration: false,
    })
  ).toEqual([
    ['Flownode A', '1', '--'],
    ['Flownode B', '2', '0'],
  ]);
});

it('should return correctly formatted body rows', () => {
  expect(
    getBodyRows({
      unitedResults,
      allKeys: ['a', 'b'],
      formatter: (v) => v,
      displayRelativeValue: false,
      instanceCount: [100, 100],
      displayAbsoluteValue: true,
      groupedByDuration: false,
    })
  ).toEqual([
    ['a', '1', '--'],
    ['b', '2', '0'],
  ]);
});

it('should hide absolute values when sepcified from body rows', () => {
  expect(
    getBodyRows({
      unitedResults,
      allKeys: ['a', 'b'],
      formatter: (v) => v,
      displayRelativeValue: false,
      instanceCount: [100, 100],
      displayAbsoluteValue: false,
      groupedByDuration: false,
    })
  ).toEqual([['a'], ['b']]);
});

it('should return correct table label structure', () => {
  expect(
    getFormattedLabels(
      [
        ['key', 'value'],
        ['key', 'value'],
      ],
      ['Report A', 'Report B'],
      ['ReportIdA', 'ReportIdB'],
      false,
      true
    )
  ).toEqual([
    {label: 'Report A', id: 'ReportIdA', columns: ['value']},
    {label: 'Report B', id: 'ReportIdB', columns: ['value']},
  ]);
});

it('should return correct combined table report data properties', () => {
  const report = {
    name: 'report A',
    combined: false,
    data: {
      view: {
        properties: ['frequency'],
      },
      groupBy: {
        type: 'startDate',
        value: {
          unit: 'day',
        },
      },
      visualization: 'table',
      configuration: {sorting: null},
    },
    result: {
      instanceCount: 100,
      measures: [
        {
          property: 'frequency',
          data: [
            {key: '2015-03-25T12:00:00Z', label: '2015-03-25T12:00:00Z', value: 2},
            {key: '2015-03-26T12:00:00Z', label: '2015-03-26T12:00:00Z', value: 3},
          ],
        },
      ],
    },
  };

  const combinedReport = {
    combined: true,
    data: {
      configuration: {sorting: null},
      reports: [{id: 'report A'}, {id: 'report B'}],
    },
    result: {
      'report A': report,
      'report B': report,
    },
  };
  const tableProps = getCombinedTableProps(
    combinedReport.result,
    combinedReport.data.reports,
    false,
    true
  );

  expect(tableProps).toEqual({
    combinedResult: [
      [
        {
          property: 'frequency',
          data: [
            {key: '2015-03-25T12:00:00Z', label: '2015-03-25', value: 2},
            {key: '2015-03-26T12:00:00Z', label: '2015-03-26', value: 3},
          ],
        },
      ],
      [
        {
          property: 'frequency',
          data: [
            {key: '2015-03-25T12:00:00Z', label: '2015-03-25', value: 2},
            {key: '2015-03-26T12:00:00Z', label: '2015-03-26', value: 3},
          ],
        },
      ],
    ],
    labels: [
      ['foo', 'Flow Node: Count'],
      ['foo', 'Flow Node: Count'],
    ],
    instanceCount: [100, 100],
    reportsNames: ['report A', 'report A'],
    reportsIds: ['report A', 'report B'],
  });
});

describe('sortColumns', () => {
  const head = [
    'processInstanceId',
    'prop2',
    {type: 'variables', id: 'variable:var1', label: 'Var: var1'},
    {type: 'variables', id: 'variable:var2', label: 'Var: var2'},
  ];
  const body = [
    ['foo', 'bar', '12', ''],
    ['xyz', 'abc', '', 'true'],
  ];

  it('should apply column order', () => {
    const columnOrder = ['prop2', 'processInstanceId', 'variable:var1', 'variable:var2'];

    expect(sortColumns(head, body, columnOrder)).toEqual({
      sortedHead: [
        'prop2',
        'processInstanceId',
        {type: 'variables', id: 'variable:var1', label: 'Var: var1'},
        {type: 'variables', id: 'variable:var2', label: 'Var: var2'},
      ],
      sortedBody: [
        ['bar', 'foo', '12', ''],
        ['abc', 'xyz', '', 'true'],
      ],
    });
  });

  it('should sort groups of columns', () => {
    const head = [
      'processInstanceId',
      {id: 'prop2', columns: ['a']},
      {id: 'variables', columns: ['var1', 'var2']},
    ];
    const columnOrder = ['variables', 'processInstanceId', 'prop2'];

    expect(sortColumns(head, body, columnOrder)).toEqual({
      sortedHead: [
        {columns: ['var1', 'var2'], id: 'variables'},
        'processInstanceId',
        {columns: ['a'], id: 'prop2'},
      ],
      sortedBody: [
        ['12', '', 'foo', 'bar'],
        ['', 'true', 'xyz', 'abc'],
      ],
    });
  });

  it('should return the original head and body if no sorting is applied', () => {
    const columnOrder = [];

    const {sortedHead, sortedBody} = sortColumns(head, body, columnOrder);

    expect(sortedHead).toBe(head);
    expect(sortedBody).toBe(body);
  });

  it('should append columns without specified column order', () => {
    const columnOrder = ['processInstanceId', 'variable:var1'];

    expect(sortColumns(head, body, columnOrder)).toEqual({
      sortedBody: [
        ['foo', '12', 'bar', ''],
        ['xyz', '', 'abc', 'true'],
      ],
      sortedHead: [
        'processInstanceId',
        {id: 'variable:var1', label: 'Var: var1', type: 'variables'},
        'prop2',
        {id: 'variable:var2', label: 'Var: var2', type: 'variables'},
      ],
    });
  });
});

it('should check if column is enabled based on included values', () => {
  const isVisible = isVisibleColumn('test', {
    excludedColumns: ['test'],
    includedColumns: ['test'],
    includeNewVariables: false,
  });

  expect(isVisible).toBe(true);
});

it('should check if column is enabled based on excluded values', () => {
  const isVisible = isVisibleColumn('test', {
    excludedColumns: ['test'],
    includedColumns: ['test'],
    includeNewVariables: true,
  });

  expect(isVisible).toBe(false);
});
