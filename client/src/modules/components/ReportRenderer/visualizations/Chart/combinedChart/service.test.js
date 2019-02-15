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
    processInstanceCount: 100,
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
      '2015-03-25T12:00:00Z': 2,
      '2015-03-26T12:00:00Z': 3
    }
  };

  const result = {
    'report A': report,
    'report B': report
  };

  const data = {
    ...report.data,
    reportIds: ['report A', 'report B']
  };

  const chartProps = getCombinedChartProps(result, data);

  expect(chartProps).toEqual({
    resultArr: [
      {'2015-03-25T12:00:00Z': 2, '2015-03-26T12:00:00Z': 3},
      {'2015-03-25T12:00:00Z': 2, '2015-03-26T12:00:00Z': 3}
    ],
    reportsNames: ['report A', 'report A']
  });
});

it('should convert results of a combined number report to a correctly formatted barchart data', () => {
  const NumberReportA = {
    name: 'report A',
    data: {
      visualization: 'number'
    },
    result: 100
  };

  const result = {
    NumberReportA: NumberReportA,
    NumberReportB: NumberReportA
  };

  const chartProps = getCombinedChartProps(result, {visualization: 'number'});

  expect(chartProps).toEqual({
    resultArr: [{'report A': 100}, {'report A': 100}],
    reportsNames: ['report A', 'report A']
  });
});
