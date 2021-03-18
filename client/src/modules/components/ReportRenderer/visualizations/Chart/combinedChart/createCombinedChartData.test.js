/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getCombinedChartProps} from './service';
import createCombinedChartData from './createCombinedChartData';

jest.mock('../defaultChart/createDefaultChartOptions', () => ({createDatasetOptions: jest.fn()}));
jest.mock('./service', () => ({getCombinedChartProps: jest.fn()}));

const createReport = ({reportA, reportB, groupByType}) => {
  getCombinedChartProps.mockReturnValue({
    reportsNames: ['report A', 'report B'],
    resultArr: [reportA.data, reportB.data],
    reportColors: [],
  });

  return {
    result: {
      data: {
        reportA: {
          name: 'Report A',
          result: {
            data: reportA.data,
          },
        },
        reportB: {
          name: 'Report B',
          result: {
            data: reportB.data,
          },
        },
      },
    },
    data: {
      groupBy: {
        type: groupByType || 'startDate',
        value: 'month',
      },
      reports: [
        {id: 'reportA', color: reportA.color},
        {id: 'reportB', color: reportB.color},
      ],
      visualization: 'line',
    },
    combined: true,
  };
};

it('should return correct chart data object for a combined report', () => {
  const reportA = {
    data: [
      {key: 'foo', value: 123, label: 'Flownode Foo'},
      {key: 'bar', value: 5, label: 'Barrrr'},
    ],
    color: 'blue',
  };

  const reportB = {
    data: [
      {key: 'foo', value: 1, label: 'Flownode Foo'},
      {key: 'dar', value: 3, label: 'Flownode DAR'},
    ],
    color: 'yellow',
  };

  const chartData = createCombinedChartData({
    report: createReport({reportA, reportB, groupByType: 'flowNodes'}),
    targetValue: false,
    theme: 'light',
  });

  expect(chartData).toMatchSnapshot();
});

it('should return correct chart data object for a combined report with correct date order', () => {
  const reportA = {
    data: [{key: '2017-12-27T14:21:56.000', value: 123, label: 'Dec 2017'}],
    color: 'blue',
  };

  const reportB = {
    data: [{key: '2017-03-27T14:21:56.000', value: 5, label: 'Mar 2017'}],
    color: 'yellow',
  };

  const chartData = createCombinedChartData({
    report: createReport({reportA, reportB}),
    targetValue: false,
    theme: 'light',
  });

  expect(chartData).toMatchSnapshot();
});
