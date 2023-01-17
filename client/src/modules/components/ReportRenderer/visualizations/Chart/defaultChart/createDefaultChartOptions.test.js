/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {determineBarColor} from '../colorsUtils';
import {
  createDatasetOptions,
  default as createDefaultChartOptions,
  createBarOptions,
} from './createDefaultChartOptions';

jest.mock('../colorsUtils', () => ({
  ...jest.requireActual('../colorsUtils'),
  determineBarColor: jest.fn(),
}));

it('should create dataset option for barchart report', () => {
  const data = [
    {key: 'foo', value: 123},
    {key: 'bar', value: 5},
  ];
  const options = createDatasetOptions({
    type: 'bar',
    data,
    targetValue: false,
    datasetColor: 'testColor',
    isStriped: false,
    isDark: false,
  });
  expect(options).toEqual({
    backgroundColor: 'testColor',
    borderColor: 'testColor',
    hoverBackgroundColor: 'testColor',
    borderWidth: 1,
    legendColor: 'testColor',
  });
});

it('should invoke determineBarColor when targetValue is present', () => {
  determineBarColor.mockClear();
  const data = [
    {key: 'foo', value: 123},
    {key: 'bar', value: 5},
  ];
  createDatasetOptions({
    type: 'bar',
    data,
    targetValue: true,
    datasetColor: 'testColor',
    isStriped: true,
    isDark: false,
  });

  expect(determineBarColor).toHaveBeenCalledWith(true, data, 'testColor', true, false);
});

it('should not invoke determineBarColor for stackedBar', () => {
  determineBarColor.mockClear();
  const data = [
    {key: 'foo', value: 123},
    {key: 'bar', value: 5},
  ];
  createDatasetOptions({
    type: 'bar',
    data,
    targetValue: true,
    datasetColor: 'testColor',
    isStriped: true,
    isDark: false,
    stackedBar: true,
  });

  expect(determineBarColor).not.toHaveBeenCalled();
});

it('should create dataset option for pie reports', () => {
  const data = [
    {key: 'foo', value: 123},
    {key: 'bar', value: 5},
  ];
  const options = createDatasetOptions({
    type: 'pie',
    data,
    targetValue: false,
    datasetColor: 'testColor',
    isStriped: false,
    isDark: false,
  });
  expect(options).toEqual({
    backgroundColor: ['#aec7e9', '#6391d2'],
    borderColor: '#fff',
    borderWidth: undefined,
    hoverBackgroundColor: ['#aec7e9', '#6391d2'],
  });
});

it('should create default chart options', () => {
  expect(
    createDefaultChartOptions({
      report: {data: {visualization: 'pie', configuration: {}}, result: {measures: [{data: []}]}},
    })
  ).toMatchSnapshot();
});

it('should create bar options', () => {
  const chartConfig = createBarOptions({
    configuration: {},
    measures: [{property: 'frequency', data: []}],
  });

  expect(chartConfig.indexAxis).toBe('x');
  expect(Object.keys(chartConfig.scales).length).toBe(2);
});

it('should create multi-axis chart for multi-measure reports', () => {
  const chartConfig = createDefaultChartOptions({
    report: {
      data: {
        visualization: 'bar',
        configuration: {xLabel: 'Flow Nodes'},
        view: {properties: ['frequency', 'duration'], entity: 'flowNode'},
        groupBy: {type: 'flowNodes'},
      },
      result: {
        measures: [
          {property: 'frequency', data: [{key: 'a', value: 123, label: 'a'}]},
          {property: 'duration', data: [{key: 'a', value: 9001, label: 'a'}]},
        ],
      },
    },
  });

  expect(Object.keys(chartConfig.scales).length).toBe(3);

  expect(chartConfig.scales['axis-0'].title.text).toBe('Flow Node Count');
  expect(chartConfig.scales['axis-0'].position).toBe('left');

  expect(chartConfig.scales['axis-1'].title.text).toBe('Flow Node Duration');
  expect(chartConfig.scales['axis-1'].position).toBe('right');

  expect(chartConfig.scales['x'].title.text).toBe('Flow Nodes');
});

it('should switch axis positions when horizontalBar config is eanbled', () => {
  const chartConfig = createDefaultChartOptions({
    report: {
      data: {
        visualization: 'bar',
        configuration: {horizontalBar: true},
        view: {properties: ['frequency', 'duration'], entity: 'flowNode'},
        groupBy: {type: 'flowNodes'},
      },
      result: {
        measures: [
          {property: 'frequency', data: [{key: 'a', value: 123, label: 'a'}]},
          {property: 'duration', data: [{key: 'a', value: 9001, label: 'a'}]},
        ],
      },
    },
  });

  expect(chartConfig.indexAxis).toBe('y');

  expect(chartConfig.scales['axis-0'].position).toBe('bottom');
  expect(chartConfig.scales['axis-0'].axis).toBe('x');

  expect(chartConfig.scales['axis-1'].position).toBe('top');
  expect(chartConfig.scales['axis-0'].axis).toBe('x');

  expect(chartConfig.scales['y'].axis).toBe('y');
});

it('should switch tooltip alignment of an item when surpassing 70% of the available area', () => {
  const chartConfig = createDefaultChartOptions({
    report: {
      data: {
        visualization: 'bar',
        configuration: {horizontalBar: true},
        view: {properties: ['frequency', 'duration'], entity: 'flowNode'},
        groupBy: {type: 'flowNodes'},
      },
      result: {
        measures: [{property: 'frequency', data: [{key: 'a', value: 123, label: 'a'}]}],
      },
    },
  });

  const getPixelForValue = jest.fn().mockReturnValueOnce(5);
  const context = {
    dataIndex: 0,
    dataset: {xAxisID: 'axis-0', data: []},
    chart: {chartArea: {left: 0, right: 20}, scales: {'axis-0': {getPixelForValue}}},
  };

  const alignment = chartConfig.plugins.datalabels.align(context);
  expect(alignment).toBe('end');
  getPixelForValue.mockReturnValueOnce(19);
  const newAlignment = chartConfig.plugins.datalabels.align(context);
  expect(newAlignment).toBe('start');
});

it('should set axis-0 scale step to integers if chart has frequency measure', () => {
  const chartConfig = createDefaultChartOptions({
    report: {
      data: {
        visualization: 'bar',
        configuration: {xLabel: 'Flow Nodes'},
        view: {properties: ['frequency', 'duration'], entity: 'flowNode'},
        groupBy: {type: 'flowNodes'},
      },
      result: {
        measures: [
          {property: 'frequency', data: [{key: 'a', value: 123, label: 'a'}]},
          {property: 'duration', data: [{key: 'a', value: 9001, label: 'a'}]},
        ],
      },
    },
  });

  expect(chartConfig.scales['axis-0'].ticks.precision).toBe(0);
});
