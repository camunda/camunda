import createCombinedChartData from './createCombinedChartData';

import {uniteResults} from '../../service';

jest.mock('../../service', () => {
  return {
    uniteResults: jest.fn().mockReturnValue([{foo: 123, bar: 5}])
  };
});

it('should return correct chart data object for a combined report', () => {
  const result = {
    reportA: {name: 'Report A', result: {foo: 123, bar: 5}},
    reportB: {name: 'Report B', result: {foo: 1, dar: 3}}
  };

  uniteResults.mockClear();
  uniteResults.mockReturnValue([result.reportA.result, result.reportB.result]);

  const chartData = createCombinedChartData({
    report: {
      result,
      data: {
        groupBy: {
          type: '',
          value: ''
        },
        view: {},
        reportIds: ['reportA', 'reportB'],
        configuration: {reportColors: ['blue', 'yellow']},
        visualization: 'line'
      },
      combined: true
    },
    targetValue: false,
    theme: 'light'
  });

  expect(chartData).toEqual({
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
