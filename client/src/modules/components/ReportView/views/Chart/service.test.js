import {
  createDurationFormattingOptions,
  formatTooltip,
  getFormattedTargetValue,
  generateLegendLabels,
  calculateLinePosition,
  getTooltipLabelColor,
  getCombinedChartProps
} from './service';

import {formatters} from 'services';

const {convertToMilliseconds} = formatters;

jest.mock('services', () => {
  return {
    formatters: {convertToMilliseconds: jest.fn(), formatReportResult: (data, result) => result}
  };
});

it('should show nice ticks for duration formats on the y axis', () => {
  const data = {foo: 7 * 24 * 60 * 60 * 1000};

  const config = createDurationFormattingOptions(data, 0);

  expect(config.stepSize).toBe(1 * 24 * 60 * 60 * 1000);
  expect(config.callback(3 * 24 * 60 * 60 * 1000)).toBe('3d');
});

it('should include the relative value in tooltips', () => {
  const response = formatTooltip(
    {index: 0, datasetIndex: 0},
    {datasets: [{data: [2.5]}]},
    false,
    {},
    v => v,
    [5],
    'frequency',
    'bar',
    false
  );

  expect(response).toBe('2.5 (50%)');
});

it('should generate correct colors in label tooltips for pie charts ', () => {
  const response = getTooltipLabelColor(
    {index: 0, datasetIndex: 0},
    {data: {datasets: [{backgroundColor: ['testColor1'], legendColor: 'testColor2'}]}},
    'pie'
  );

  expect(response).toEqual({
    borderColor: 'testColor1',
    backgroundColor: 'testColor1'
  });
});

it('should generate correct colors in label tooltips for bar charts', () => {
  const response = getTooltipLabelColor(
    {index: 0, datasetIndex: 0},
    {data: {datasets: [{backgroundColor: ['testColor1'], legendColor: 'testColor2'}]}},
    'bar'
  );

  expect(response).toEqual({
    borderColor: 'testColor2',
    backgroundColor: 'testColor2'
  });
});

it('should set LineAt option to target value if it is active', () => {
  const value = getFormattedTargetValue({value: 10});
  expect(value).toBe(10);
});

it('should invoke convertToMilliSeconds when target value is set to Date Format', () => {
  getFormattedTargetValue({value: 10, unit: 'millis'});
  expect(convertToMilliseconds).toBeCalledWith(10, 'millis');
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

it('should calculate the correct position for the target value line', () => {
  expect(
    calculateLinePosition({
      scales: {
        test: {
          max: 100,
          height: 100,
          top: 0
        }
      },
      options: {
        scales: {
          yAxes: [{id: 'test'}]
        },
        lineAt: 20
      }
    })
  ).toBe(80); // inverted y axis: height - lineAt = 100 - 20 = 80
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
