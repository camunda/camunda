/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {Chart} from './Chart';
import createDefaultChartConfig from './defaultChart';
import createHyperChartConfig from './hyperChart';
import createTargetLineConfig from './targetLineChart';

jest.mock('./targetLineChart', () => jest.fn());
jest.mock('./hyperChart', () => jest.fn());
jest.mock('./defaultChart', () => jest.fn());

const report = {
  data: {
    configuration: {targetValue: {active: false}},
    view: {properties: ['frequency']},
    groupBy: {
      value: '',
      type: '',
    },
  },
};

it('should destroy chart if no data is provided', () => {
  const node = shallow(<Chart report={report} errorMessage="Error" />);

  expect(node.chart).toBe(undefined);
});

it('should use the special targetLine type when target values are enabled on a line chart', () => {
  const targetValue = {targetValue: {active: true, countChart: {isBelow: true, value: 1}}};

  shallow(
    <Chart
      report={{
        ...report,
        result: {data: {foo: 123}},
        data: {...report.data, visualization: 'line', configuration: targetValue},
      }}
    />
  );

  expect(createTargetLineConfig).toHaveBeenCalled();
});

it('should render hyper chart if report is hyper', () => {
  shallow(
    <Chart
      report={{
        ...report,
        result: {data: null},
        hyper: true,
      }}
    />
  );

  expect(createHyperChartConfig).toHaveBeenCalled();
});

it('should render default normal chart if report is a single report', () => {
  shallow(
    <Chart
      report={{
        ...report,
        result: {data: {}},
      }}
    />
  );

  expect(createDefaultChartConfig).toHaveBeenCalled();
});
