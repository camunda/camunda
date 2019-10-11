/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getFormattedLabels, getBodyRows, getCombinedTableProps} from './service';

jest.mock('request', () => ({
  get: jest.fn()
}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    reportConfig: {
      process: {
        getLabelFor: () => 'foo',
        options: {
          view: {foo: {data: 'foo', label: 'viewfoo'}},
          groupBy: {
            foo: {data: 'foo', label: 'groupbyfoo'}
          }
        }
      }
    }
  };
});

it('should apply flow node names to the body rows', () => {
  expect(
    getBodyRows(
      [[{key: 'a', value: 1}, {key: 'b', value: 2}], [{key: 'a', value: ''}, {key: 'b', value: 0}]],
      ['a', 'b'],
      v => v,
      false,
      [100, 100],
      true,
      {
        a: 'Flownode A',
        b: 'Flownode B'
      }
    )
  ).toEqual([['Flownode A', 1, ''], ['Flownode B', 2, 0]]);
});

it('should return correctly formatted body rows', () => {
  expect(
    getBodyRows(
      [[{key: 'a', value: 1}, {key: 'b', value: 2}], [{key: 'a', value: ''}, {key: 'b', value: 0}]],
      ['a', 'b'],
      v => v,
      false,
      [100, 100],
      true
    )
  ).toEqual([['a', 1, ''], ['b', 2, 0]]);
});

it('should hide absolute values when sepcified from body rows', () => {
  expect(
    getBodyRows(
      [[{key: 'a', value: 1}, {key: 'b', value: 2}], [{key: 'a', value: ''}, {key: 'b', value: 1}]],
      ['a', 'b'],
      v => v,
      false,
      [100, 100],
      false
    )
  ).toEqual([['a'], ['b']]);
});

it('should return correct table label structure', () => {
  expect(
    getFormattedLabels([['key', 'value'], ['key', 'value']], ['Report A', 'Report B'], false, true)
  ).toEqual([{label: 'Report A', columns: ['value']}, {label: 'Report B', columns: ['value']}]);
});

it('should hide absolute values when specified from labels', () => {
  expect(
    getFormattedLabels([['key', 'value'], ['key', 'value']], ['Report A', 'Report B'], false, false)
  ).toEqual([{columns: [], label: 'Report A'}, {columns: [], label: 'Report B'}]);
});

it('should return correct combined table report data properties', () => {
  const report = {
    name: 'report A',
    combined: false,
    data: {
      view: {
        property: 'foo'
      },
      groupBy: {
        type: 'startDate',
        value: {
          unit: 'day'
        }
      },
      visualization: 'table',
      configuration: {sorting: null}
    },
    result: {
      instanceCount: 100,
      data: [
        {key: '2015-03-25T12:00:00Z', label: '2015-03-25T12:00:00Z', value: 2},
        {key: '2015-03-26T12:00:00Z', label: '2015-03-26T12:00:00Z', value: 3}
      ]
    }
  };

  const combinedReport = {
    combined: true,
    data: {
      configuration: {sorting: null},
      reports: [{id: 'report A'}, {id: 'report B'}]
    },
    result: {
      'report A': report,
      'report B': report
    }
  };
  const tableProps = getCombinedTableProps(combinedReport.result, combinedReport.data.reports);

  expect(tableProps).toEqual({
    combinedResult: [
      [
        {key: '2015-03-25T12:00:00Z', label: '2015-03-25', value: 2},
        {key: '2015-03-26T12:00:00Z', label: '2015-03-26', value: 3}
      ],
      [
        {key: '2015-03-25T12:00:00Z', label: '2015-03-25', value: 2},
        {key: '2015-03-26T12:00:00Z', label: '2015-03-26', value: 3}
      ]
    ],
    labels: [['foo', 'foo'], ['foo', 'foo']],
    instanceCount: [100, 100],
    reportsNames: ['report A', 'report A']
  });
});
