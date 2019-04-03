/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import createDefaultChartData from './createDefaultChartData';

import {uniteResults} from '../../service';

jest.mock('../../service', () => {
  return {
    uniteResults: jest.fn().mockReturnValue([{foo: 123, bar: 5}])
  };
});

jest.mock('../colorsUtils', () => {
  const rest = jest.requireActual('../colorsUtils');
  return {
    ...rest,
    createColors: jest.fn().mockReturnValue([])
  };
});

it('should return correct chart data object for a single report', () => {
  uniteResults.mockClear();
  const result = {data: {foo: 123, bar: 5}};
  uniteResults.mockReturnValue([result]);

  const chartData = createDefaultChartData({
    report: {
      result,
      data: {
        configuration: {color: 'testColor'},
        visualization: 'line',
        groupBy: {
          type: '',
          value: ''
        },
        view: {}
      },
      targetValue: false,
      combined: false
    },
    theme: 'light'
  });

  expect(chartData).toEqual({
    labels: ['foo', 'bar'],
    datasets: [
      {
        legendColor: 'testColor',
        data: [123, 5],
        borderColor: 'testColor',
        backgroundColor: 'transparent',
        borderWidth: 2
      }
    ]
  });
});
