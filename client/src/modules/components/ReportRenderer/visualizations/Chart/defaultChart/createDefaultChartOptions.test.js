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
  const options = createDatasetOptions('bar', data, false, 'testColor', false, false);
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
  const options = createDatasetOptions('pie', data, false, 'testColor', false, false);
  expect(options).toEqual({
    backgroundColor: ['#aec7e9', '#f68077'],
    borderColor: '#fff',
    borderWidth: undefined,
  });
});

it('should create default chart options', () => {
  expect(
    createDefaultChartOptions({
      report: {data: {visualization: 'pie', configuration: {}}, result: {data: []}},
    })
  ).toMatchSnapshot();
});

it('should create bar options', () => {
  expect(
    createBarOptions({
      configuration: {},
    })
  ).toMatchSnapshot();
});
