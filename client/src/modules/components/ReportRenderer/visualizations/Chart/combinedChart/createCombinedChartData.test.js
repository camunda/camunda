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
          type: 'flowNodes',
          value: ''
        },
        view: {},
        reports: [{id: 'reportA', color: 'blue'}, {id: 'reportB', color: 'yellow'}],
        configuration: {},
        visualization: 'line'
      },
      combined: true
    },
    flowNodeNames: {foo: 'Flownode Foo', bar: 'Barrrr', dar: 'Flownode DAR'},
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
    labels: ['Flownode Foo', 'Barrrr', 'Flownode DAR']
  });
});
