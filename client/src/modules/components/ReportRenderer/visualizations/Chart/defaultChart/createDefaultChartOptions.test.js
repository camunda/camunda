/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  createDatasetOptions,
  default as createDefaultChartOptions,
  createBarOptions,
} from './createDefaultChartOptions';

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
    borderWidth: 1,
    legendColor: 'testColor',
  });
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
    backgroundColor: ['#aec7e9', '#f68077'],
    borderColor: '#fff',
    borderWidth: undefined,
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
      isMultiMeasure: false,
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
