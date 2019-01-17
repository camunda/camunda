import createChartData from './createChartData';

import {uniteResults} from '../service';

jest.mock('../service', () => {
  return {
    uniteResults: jest.fn().mockReturnValue([{foo: 123, bar: 5}])
  };
});

jest.mock('./colorsUtils', () => {
  const rest = jest.requireActual('./colorsUtils');
  return {
    ...rest,
    createColors: jest.fn().mockReturnValue([])
  };
});

it('should create two datasets for line chart with target values', () => {
  const data = [{foo: 123, bar: 5, dar: 5}];
  const targetValue = {active: true, values: {target: 10}};

  const chartData = createChartData({
    data,
    reportsNames: ['test'],
    configuration: {color: ['blue']},
    type: 'line',
    targetValue,
    combined: false,
    theme: 'light',
    isDate: false
  });
  expect(chartData.datasets).toHaveLength(2);
});

it('should create two datasets for each report in combined line charts with target values', () => {
  uniteResults.mockClear();

  const data = {foo: 123, bar: 5, dar: 5};
  uniteResults.mockReturnValue([data, data]);
  const targetValue = {active: true, values: {target: 10}};
  const chartData = createChartData({
    data: [data, data],
    reportsNames: ['test1', 'test2'],
    configuration: {color: ['blue', 'yellow']},
    type: 'line',
    targetValue,
    combined: true,
    theme: 'light',
    isDate: false
  });

  expect(chartData.datasets).toHaveLength(4);
});

it('should return correct chart data object for a single report', () => {
  uniteResults.mockClear();
  const data = {foo: 123, bar: 5};
  uniteResults.mockReturnValue([data]);

  const result = createChartData({
    data,
    reportsNames: ['test'],
    configuration: {color: ['testColor']},
    type: 'line',
    targetValue: {active: false},
    combined: false,
    theme: 'light',
    isDate: false
  });

  expect(result).toEqual({
    labels: ['foo', 'bar'],
    datasets: [
      {
        label: 'test',
        legendColor: 'testColor',
        data: [123, 5],
        borderColor: 'testColor',
        backgroundColor: 'transparent',
        borderWidth: 2
      }
    ]
  });
});

it('should return correct chart data object for a combined report', () => {
  const data = [{foo: 123, bar: 5}, {foo: 1, dar: 3}];

  uniteResults.mockClear();
  uniteResults.mockReturnValue(data);

  const result = createChartData({
    data,
    reportsNames: ['Report A', 'Report B'],
    configuration: {color: ['blue', 'yellow']},
    type: 'line',
    targetValue: {active: false},
    combined: true,
    theme: 'light',
    isDate: false
  });

  expect(result).toEqual({
    datasets: [
      {
        backgroundColor: 'transparent',
        borderColor: 'blue',
        borderWidth: 2,
        data: [123, 5],
        label: 'Report A',
        legendColor: 'blue'
      },
      {
        backgroundColor: 'transparent',
        borderColor: 'yellow',
        borderWidth: 2,
        data: [1, 3],
        label: 'Report B',
        legendColor: 'yellow'
      }
    ],
    labels: ['foo', 'bar', 'dar']
  });
});
