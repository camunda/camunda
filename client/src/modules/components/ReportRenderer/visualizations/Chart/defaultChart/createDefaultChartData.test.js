/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import createDefaultChartData from './createDefaultChartData';

it('should return correct chart data object for a single report', () => {
  const result = {
    measures: [
      {
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
        view: {properties: ['frequency'], entity: 'flowNode'},
      },
      targetValue: false,
      combined: false,
      reportType: 'process',
    },
    theme: 'light',
  });

  expect(chartData).toEqual({
    labels: ['foo', 'bar'],
    datasets: [
      {
        backgroundColor: 'testColor',
        borderColor: 'testColor',
        borderWidth: 2,
        data: [123, 5],
        fill: false,
        label: 'Flow Node Duration',
        legendColor: 'testColor',
        yAxisID: 'axis-0',
      },
    ],
  });
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
