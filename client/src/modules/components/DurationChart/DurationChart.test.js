/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {Chart} from 'chart.js';
import DurationChart from './DurationChart';

const data = [
  {key: '5', value: '3', outlier: true},
  {key: '1', value: '20', outlier: false},
];

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  formatters: {
    createDurationFormattingOptions: jest.fn(),
    duration: (val) => 'formatted ' + val,
  },
}));

it('should construct a bar Chart with the node data', () => {
  const testColors = ['red', 'blue'];
  shallow(<DurationChart data={data} colors={testColors} />);

  runAllEffects();

  expect(Chart).toHaveBeenCalled();
  expect(Chart.mock.calls[0][1].type).toBe('bar');
  const {datasets, labels} = Chart.mock.calls[0][1].data;

  expect(datasets[0].data).toEqual(['3', '20']);
  expect(labels).toEqual(['5', '1']);
  expect(datasets[0].backgroundColor).toEqual(testColors);
  expect(datasets[0].borderColor).toEqual(testColors);
});

it('should create correct chart options', () => {
  shallow(<DurationChart data={data} />);

  runAllEffects();

  expect(Chart.mock.calls[0][1].options).toMatchSnapshot();
});

it('should format tooltip durations', () => {
  shallow(<DurationChart data={data} />);

  runAllEffects();

  const durationInMs = 1020;
  expect(
    Chart.mock.calls[0][1].options.plugins.tooltip.callbacks.label({
      label: durationInMs,
      dataset: {data: [1]},
      dataIndex: 0,
    })
  ).toContain('formatted ' + durationInMs);
});
