/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import createDefaultChartData from './createDefaultChartData';

jest.mock('./createDefaultChartOptions', () => ({createDatasetOptions: ({type}) => ({type})}));

it('should return correct chart data object for a single report', () => {
  const result = {
    measures: [
      {
        property: 'duration',
        aggregationType: {type: 'avg', value: null},
        data: [
          {key: 'foo', value: 123},
          {key: 'bar', value: 5},
        ],
      },
    ],
  };

  const chartData = createDefaultChartData({
    report: {
      result,
      data: {
        configuration: {color: 'testColor'},
        visualization: 'line',
        groupBy: {
          type: '',
          value: '',
        },
        view: {properties: ['duration'], entity: 'flowNode'},
      },
      targetValue: false,
      combined: false,
      reportType: 'process',
    },
    theme: 'light',
  });

  expect(chartData).toMatchSnapshot();
});

it('should return correct chart data object for multi-measure report', () => {
  expect(
    createDefaultChartData({
      report: {
        data: {
          configuration: {color: 'testColor'},
          visualization: 'line',
          groupBy: {
            type: '',
            value: '',
          },
          view: {properties: ['frequency', 'duration'], entity: 'flowNode'},
        },
        targetValue: false,
        combined: false,
        result: {
          measures: [
            {
              property: 'frequency',
              data: [
                {key: 'foo', value: 123},
                {key: 'bar', value: 5},
              ],
            },
            {
              property: 'duration',
              aggregationType: {type: 'avg', value: null},
              data: [
                {key: 'foo', value: 175824},
                {key: 'bar', value: 592754},
              ],
            },
          ],
        },
        reportType: 'process',
      },
      theme: 'light',
    })
  ).toMatchSnapshot();
});

it('should assign line/bar visualization to dataset according to measureVisualizations configuration', () => {
  const result = {
    measures: [
      {
        property: 'frequency',
        data: [
          {key: 'foo', value: 123},
          {key: 'bar', value: 5},
        ],
      },
      {
        property: 'duration',
        aggregationType: {type: 'avg', value: null},
        data: [
          {key: 'foo', value: 175824},
          {key: 'bar', value: 592754},
        ],
      },
    ],
  };

  const chartData = createDefaultChartData({
    report: {
      result,
      data: {
        configuration: {
          color: 'testColor',
          measureVisualizations: {frequency: 'line', duration: 'bar'},
        },
        visualization: 'barLine',
        groupBy: {
          type: '',
          value: '',
          view: {properties: ['duration'], entity: 'flowNode'},
        },
        view: {properties: ['frequency', 'duration'], entity: 'flowNode'},
      },
      targetValue: false,
      combined: false,
      reportType: 'process',
    },
    theme: 'light',
  });

  expect(chartData.datasets[0].type).toBe('line');
  expect(chartData.datasets[0].order).toBe(0);
  expect(chartData.datasets[1].type).toBe('bar');
  expect(chartData.datasets[1].order).toBe(1);
});

it('should set the axis id for charts', () => {
  const result = {
    measures: [
      {property: 'frequency', data: []},
      {property: 'duration', data: []},
    ],
  };

  const report = {
    result,
    data: {
      configuration: {},
      aggregationType: {type: 'avg', value: null},
      visualization: 'bar',
      view: {properties: ['frequency', 'duration'], entity: 'flowNode'},
      groupBy: {},
    },
  };

  const chartData = createDefaultChartData({report});

  expect(chartData.datasets[0].yAxisID).toBe('axis-0');
  expect(chartData.datasets[1].yAxisID).toBe('axis-1');

  report.data.configuration.horizontalBar = true;
  const horizontalBarData = createDefaultChartData({report});

  expect(horizontalBarData.datasets[0].yAxisID).not.toBeDefined();
  expect(horizontalBarData.datasets[0].xAxisID).toBe('axis-0');
  expect(horizontalBarData.datasets[1].xAxisID).toBe('axis-1');
});
