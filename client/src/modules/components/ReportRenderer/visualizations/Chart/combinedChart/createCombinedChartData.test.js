/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import createCombinedChartData from './createCombinedChartData';

import {uniteResults} from '../../service';

jest.mock('../../service', () => {
  return {
    uniteResults: jest.fn().mockReturnValue([{foo: 123, bar: 5}])
  };
});

it('should return correct chart data object for a combined report', () => {
  const resultData = {
    reportA: {name: 'Report A', result: {data: {foo: 123, bar: 5}}},
    reportB: {name: 'Report B', result: {data: {foo: 1, dar: 3}}}
  };

  uniteResults.mockClear();
  uniteResults.mockReturnValue([resultData.reportA.result.data, resultData.reportB.result.data]);

  const chartData = createCombinedChartData({
    report: {
      result: {data: resultData},
      data: {
        groupBy: {
          type: 'flowNodes',
          value: ''
        },
        view: {},
        reports: [{id: 'reportA', color: 'blue'}, {id: 'reportB', color: 'yellow'}],
        configuration: {},
        visualization: 'line'
      },
      combined: true
    },
    flowNodeNames: {foo: 'Flownode Foo', bar: 'Barrrr', dar: 'Flownode DAR'},
    targetValue: false,
    theme: 'light'
  });

  expect(chartData).toEqual({
    datasets: [
      {
        backgroundColor: 'transparent',
        borderColor: 'blue',
        borderWidth: 2,
        data: [123, 5],
        label: 'Report A',
        legendColor: 'blue'
      },
      {
        backgroundColor: 'transparent',
        borderColor: 'yellow',
        borderWidth: 2,
        data: [1, 3],
        label: 'Report B',
        legendColor: 'yellow'
      }
    ],
    labels: ['Flownode Foo', 'Barrrr', 'Flownode DAR']
  });
});
