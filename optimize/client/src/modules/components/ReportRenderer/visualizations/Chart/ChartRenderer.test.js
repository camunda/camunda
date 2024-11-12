/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {Chart} from 'chart.js';

import ChartRenderer from './ChartRenderer';

jest.mock('chart.js');

const chartData = {
  type: 'visualization_type',
  data: [1, 2],
};

it('should construct a Chart', () => {
  shallow(<ChartRenderer config={chartData} />);

  expect(Chart).toHaveBeenCalled();
});

it('should use the provided type for the ChartRenderer', () => {
  Chart.mockClear();

  shallow(<ChartRenderer config={chartData} />);

  expect(Chart.mock.calls[0][1].type).toBe('visualization_type');
});
