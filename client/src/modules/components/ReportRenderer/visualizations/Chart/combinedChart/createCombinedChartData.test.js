/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createDatasetOptions} from '../defaultChart/createDefaultChartOptions';
import {getCombinedChartProps} from './service';
import createCombinedChartData from './createCombinedChartData';

jest.mock('../defaultChart/createDefaultChartOptions', () => ({createDatasetOptions: jest.fn()}));
jest.mock('./service', () => ({getCombinedChartProps: jest.fn()}));

const createReport = ({reportA, reportB, groupByType}, measures, visualization = 'line') => {
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
      measures,
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
      visualization,
      configuration: {
        measureVisualizations: {frequency: 'line', duration: 'bar'},
        stackedBar: true,
      },
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

it('should return a dataset for each measure value in multi measure reports', () => {
  const reportA = {
    data: [{key: 'flowNode1', value: 123, label: 'Dec 2017'}],
    color: 'blue',
  };

  const reportB = {
    data: [{key: 'flowNode2', value: 5, label: 'Mar 2017'}],
    color: 'yellow',
  };

  const measures = [
    {
      property: 'frequency',
      data: [{key: 'flowNode1', value: [{key: 'assignee1', value: 123}]}],
    },
    {
      property: 'duration',
      data: [{key: 'flowNode1', value: [{key: 'assignee1', value: 5536205036}]}],
    },
  ];

  const chartData = createCombinedChartData({
    report: createReport({reportA, reportB}, measures),
    targetValue: false,
    theme: 'light',
  });

  expect(chartData.datasets.length).toBe(4);
  expect(chartData.datasets[0]).toEqual({
    yAxisID: 'axis-0',
    label: 'report A - Count',
    data: [123, null],
    formatter: expect.any(Function),
    order: undefined,
    stack: 0,
  });
  expect(chartData.datasets[2]).toEqual({
    yAxisID: 'axis-1',
    label: 'report A - Duration',
    data: [123, null],
    formatter: expect.any(Function),
    order: undefined,
    stack: 1,
  });
});

it('should assign line/bar visualization to dataset according to measureVisualizations configuration', () => {
  createDatasetOptions.mockImplementation((options) => options);
  const reportA = {
    data: [{key: 'flowNode1', value: 123, label: 'Dec 2017'}],
    color: 'blue',
  };

  const reportB = {
    data: [{key: 'flowNode2', value: 5, label: 'Mar 2017'}],
    color: 'yellow',
  };

  const measures = [
    {
      property: 'frequency',
      data: [{key: 'flowNode1', value: [{key: 'assignee1', value: 123}]}],
    },
    {
      property: 'duration',
      data: [{key: 'flowNode1', value: [{key: 'assignee1', value: 5536205036}]}],
    },
  ];

  const chartData = createCombinedChartData({
    report: createReport({reportA, reportB}, measures, 'barLine'),
    targetValue: false,
    theme: 'light',
  });

  const getDataset = (label) => chartData.datasets.find((dataset) => dataset.label === label);
  expect(getDataset('report A - Count').type).toBe('line');
  expect(getDataset('report A - Count').order).toBe(0);
  expect(getDataset('report B - Count').type).toBe('line');
  expect(getDataset('report B - Count').order).toBe(0);
  expect(getDataset('report A - Duration').type).toBe('bar');
  expect(getDataset('report A - Duration').order).toBe(1);
  expect(getDataset('report B - Duration').type).toBe('bar');
  expect(getDataset('report B - Duration').order).toBe(1);
});
