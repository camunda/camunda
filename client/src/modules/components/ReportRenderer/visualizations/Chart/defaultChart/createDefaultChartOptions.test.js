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
  expect(
    createBarOptions({
      configuration: {},
      measures: [{property: 'frequency', data: []}],
    })
  ).toMatchSnapshot();
});

it('should create correct options for multi-measure charts', () => {
  expect(
    createDefaultChartOptions({
      report: {
        data: {
          visualization: 'bar',
          configuration: {},
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
    })
  ).toMatchSnapshot();
});
