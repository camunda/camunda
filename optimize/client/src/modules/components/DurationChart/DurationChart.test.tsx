/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  const chartConfiguration = ChartMock.mock.calls[0]?.[1] as ChartConfiguration;

  expect(
    // @ts-expect-error too complex to type
    chartConfiguration.options.plugins.tooltip.callbacks.label({
      label: durationInMs,
      dataset: {data: [1]},
      dataIndex: 0,
    })
  ).toContain('formatted ' + durationInMs);
});

it('should use logaritmic scale for large values', () => {
  const largeData = [
    {key: 1, value: 101, outlier: false},
    {key: 2, value: 1, outlier: true},
  ];
  shallow(<DurationChart data={largeData} colors={[]} isLogharitmic />);

  runAllEffects();

  const chartConfiguration = ChartMock.mock.calls[0]?.[1] as ChartConfiguration;
  expect(chartConfiguration.options?.scales?.y?.type).toBe('logarithmic');
});

it('should filter tooltips with 0 values', () => {
  shallow(<DurationChart data={data} colors={[]} />);

  runAllEffects();

  const chartConfiguration = ChartMock.mock.calls[0]?.[1] as ChartConfiguration;

  expect(
    // @ts-expect-error too complex to type
    chartConfiguration.options.plugins.tooltip.filter({
      formattedValue: '0',
    })
  ).toBe(false);

  expect(
    // @ts-expect-error too complex to type
    chartConfiguration.options.plugins.tooltip.filter({
      formattedValue: '1,000',
    })
  ).toBe(true);
});
