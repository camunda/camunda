/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {generateLegendLabels, getCombinedChartProps} from './service';

jest.mock('services', () => {
  return {
    formatters: {formatReportResult: (data, result) => result}
  };
});

it('should filter labels with undefined names and show correct label coloring', () => {
  const datasets = generateLegendLabels({
    data: {
      datasets: [
        {label: undefined, backgroundColor: [], legendColor: 'red'},
        {label: 'test', backgroundColor: ['blue', 'yellow'], legendColor: 'red'}
      ]
    }
  });

  expect(datasets).toEqual([{text: 'test', fillStyle: 'red', strokeStyle: 'red'}]);
});

it('should return correct cominbed chart repot data properties for single report', () => {
  const report = {
    name: 'report A',
    combined: false,
    data: {
      view: {
        operation: 'foo'
      },
      groupBy: {
        type: 'startDate',
        value: {
          unit: 'day'
        }
      },
      visualization: 'line'
    },
    result: {
      processInstanceCount: 100,
      data: {
        '2015-03-25T12:00:00Z': 2,
        '2015-03-26T12:00:00Z': 3
      }
    }
  };

  const result = {
    'report A': report,
    'report B': report
  };

  const data = {
    ...report.data,
    reports: [{id: 'report A', color: 'red'}, {id: 'report B', color: 'blue'}]
  };

  const chartProps = getCombinedChartProps(result, data);

  expect(chartProps).toEqual({
    resultArr: [
      {'2015-03-25T12:00:00Z': 2, '2015-03-26T12:00:00Z': 3},
      {'2015-03-25T12:00:00Z': 2, '2015-03-26T12:00:00Z': 3}
    ],
    reportsNames: ['report A', 'report A'],
    reportColors: ['red', 'blue']
  });
});

it('should convert results of a combined number report to a correctly formatted barchart data', () => {
  const NumberReportA = {
    name: 'report A',
    data: {
      visualization: 'number'
    },
    result: {data: 100}
  };

  const result = {
    NumberReportA: NumberReportA,
    NumberReportB: NumberReportA
  };

  const chartProps = getCombinedChartProps(result, {
    visualization: 'number',
    reports: [{id: 'NumberReportA', color: 'red'}, {id: 'NumberReportB', color: 'blue'}]
  });

  expect(chartProps).toEqual({
    resultArr: [{'report A': 100}, {'report A': 100}],
    reportsNames: ['report A', 'report A'],
    reportColors: ['red', 'blue']
  });
});
