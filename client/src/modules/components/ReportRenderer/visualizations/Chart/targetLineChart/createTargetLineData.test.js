/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import createTargetLineData from './createTargetLineData';

it('should create two datasets for line chart with target values', () => {
  const result = {data: {foo: 123, bar: 5, dar: 5}};
  const targetValue = {target: 10};

  const chartData = createTargetLineData({
    report: {
      result,
      data: {
        configuration: {color: ['blue']},
        visualization: 'line',
        groupBy: {
          type: '',
          value: ''
        }
      },
      combined: false
    },
    theme: 'light',
    targetValue
  });
  expect(chartData.datasets).toHaveLength(2);
});

it('should create two datasets for each report in combined line charts with target values', () => {
  const result = {data: {foo: 123, bar: 5, dar: 5}};
  const name = 'test1';
  const targetValue = {target: 10};
  const chartData = createTargetLineData({
    report: {
      result: {data: {reportA: {name, result}, reportB: {name, result}}},
      data: {
        reports: [{id: 'reportA', color: 'blue'}, {id: 'reportB', color: 'yellow'}],
        configuration: {},
        visualization: 'line',
        groupBy: {
          type: '',
          value: ''
        }
      },
      combined: true
    },
    targetValue,
    theme: 'light'
  });

  expect(chartData.datasets).toHaveLength(4);
});
