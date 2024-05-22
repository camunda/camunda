/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {generateLegendLabels, getCombinedChartProps} from './service';

jest.mock('services', () => {
  return {
    formatters: {formatReportResult: (data, result) => result},
  };
});

it('should filter labels with undefined names and show correct label coloring', () => {
  const datasets = generateLegendLabels({
    data: {
      datasets: [
        {label: undefined, backgroundColor: [], legendColor: 'red'},
        {label: 'test', backgroundColor: ['blue', 'yellow'], legendColor: 'red'},
      ],
    },
  });

  expect(datasets).toEqual([{text: 'test', fillStyle: 'red', strokeStyle: 'red'}]);
});

it('should return correct cominbed chart repot data properties for single report', () => {
  const report = {
    name: 'report A',
    combined: false,
    data: {
      view: {
        properties: ['foo'],
      },
      groupBy: {
        type: 'startDate',
        value: {
          unit: 'day',
        },
      },
      visualization: 'line',
    },
    result: {
      instanceCount: 100,
      measures: [
        {
          property: 'frequency',
          data: [
            {key: '2015-03-25T12:00:00Z', value: 2},
            {key: '2015-03-26T12:00:00Z', value: 3},
          ],
        },
      ],
    },
  };

  const result = {
    'report A': report,
    'report B': report,
  };

  const data = {
    ...report.data,
    reports: [
      {id: 'report A', color: 'red'},
      {id: 'report B', color: 'blue'},
    ],
  };

  const chartProps = getCombinedChartProps(result, data, 0);

  expect(chartProps).toEqual({
    resultArr: [
      [
        {key: '2015-03-25T12:00:00Z', value: 2},
        {key: '2015-03-26T12:00:00Z', value: 3},
      ],
      [
        {key: '2015-03-25T12:00:00Z', value: 2},
        {key: '2015-03-26T12:00:00Z', value: 3},
      ],
    ],
    reportsNames: ['report A', 'report A'],
    reportColors: ['red', 'blue'],
  });
});

it('should convert results of a combined number report to a correctly formatted barchart data', () => {
  const NumberReportA = {
    name: 'report A',
    id: 'NumberReportA',
    data: {
      visualization: 'number',
    },
    result: {data: 100},
  };

  const result = {
    NumberReportA: NumberReportA,
    NumberReportB: {...NumberReportA, id: 'NumberReportB', name: 'report B'},
  };

  const chartProps = getCombinedChartProps(result, {
    visualization: 'number',
    reports: [
      {id: 'NumberReportA', color: 'red'},
      {id: 'NumberReportB', color: 'blue'},
      {id: 'unauthorizedReport', color: 'green'},
    ],
  });

  expect(chartProps).toEqual({
    reportColors: ['red', 'blue'],
    reportsNames: ['report A', 'report B'],
    resultArr: [[{key: 'NumberReportA', value: 100}], [{key: 'NumberReportB', value: 100}]],
  });
});
