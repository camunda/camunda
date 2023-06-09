/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';
import {Chart, ChartConfiguration} from 'chart.js';
import {AnalysisDurationChartEntry} from 'types';

import DurationChart from './DurationChart';

jest.mock('chart.js');

const data: AnalysisDurationChartEntry[] = [
  {key: 5, value: 3, outlier: true},
  {key: 1, value: 20, outlier: false},
];

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  formatters: {
    createDurationFormattingOptions: jest.fn(),
    duration: (val: string) => 'formatted ' + val,
  },
}));

const ChartMock = Chart as jest.MockedClass<typeof Chart>;

it('should construct a bar Chart with the node data', () => {
  const testColors = ['red', 'blue'];
  shallow(<DurationChart data={data} colors={testColors} />);

  runAllEffects();

  expect(Chart).toHaveBeenCalled();
  const chartConfiguration = ChartMock.mock.calls[0]?.[1] as ChartConfiguration;
  expect(chartConfiguration.type).toBe('bar');
  const {datasets, labels} = chartConfiguration.data;

  expect(datasets[0]?.data).toEqual([3, 20]);
  expect(labels).toEqual([5, 1]);
  expect(datasets[0]?.backgroundColor).toEqual(testColors);
  expect(datasets[0]?.borderColor).toEqual(testColors);
});

it('should create correct chart options', () => {
  shallow(<DurationChart data={data} colors={[]} />);

  runAllEffects();

  expect(ChartMock.mock.calls[0]?.[1].options).toMatchSnapshot();
});

it('should format tooltip durations', () => {
  shallow(<DurationChart data={data} colors={[]} />);

  runAllEffects();

  const durationInMs = 1020;
  const chartConfiguration = ChartMock.mock.calls[0]?.[1] as any;

  expect(
    chartConfiguration.options.plugins.tooltip.callbacks.label({
      label: durationInMs,
      dataset: {data: [1]},
      dataIndex: 0,
    })
  ).toContain('formatted ' + durationInMs);
});
