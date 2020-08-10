/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Chart from 'chart.js';
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

it('should construct a bar Chart with the noda data', () => {
  shallow(<DurationChart data={data} />);

  expect(Chart).toHaveBeenCalled();
  expect(Chart.mock.calls[0][1].type).toBe('bar');
  expect(Chart.mock.calls[0][1].data).toMatchSnapshot();
});

it('should create correct chart options', () => {
  shallow(<DurationChart data={data} />);

  expect(Chart.mock.calls[0][1].options).toMatchSnapshot();
});

it('should format tooltip durations', () => {
  shallow(<DurationChart data={data} />);

  const durationInMs = 1020;
  expect(Chart.mock.calls[0][1].options.tooltips.callbacks.label({xLabel: durationInMs})).toContain(
    'formatted ' + durationInMs
  );
});
