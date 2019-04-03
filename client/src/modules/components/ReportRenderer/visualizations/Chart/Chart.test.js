/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Chart from './Chart';

import createDefaultChartConfig from './defaultChart';
import createCombinedChartConfig from './combinedChart';
import createTargetLineConfig from './targetLineChart';

jest.mock('./targetLineChart', () => jest.fn());
jest.mock('./combinedChart', () => jest.fn());
jest.mock('./defaultChart', () => jest.fn());

const report = {
  data: {
    configuration: {targetValue: {active: false}},
    view: {property: 'frequency'},
    groupBy: {
      value: '',
      type: ''
    }
  }
};

it('should display an error message for a non-object result (single number)', () => {
  const node = shallow(<Chart report={{...report, result: {data: 7}}} errorMessage="Error" />);

  expect(node.find('ReportBlankSlate').prop('errorMessage')).toBe('Error');
});

it('should display an error message if no data is provided', () => {
  const node = shallow(<Chart report={report} errorMessage="Error" />);

  expect(node.find('ReportBlankSlate').prop('errorMessage')).toBe('Error');
});

it('should not display an error message if data is valid', () => {
  const node = shallow(
    <Chart report={{...report, result: {data: {foo: 123}}}} errorMessage="Error" />
  );

  expect(node.find('ReportBlankSlate')).not.toBePresent();
});

it('should destroy chart if no data is provided', () => {
  const node = shallow(<Chart report={report} errorMessage="Error" />);

  expect(node.chart).toBe(undefined);
});

it('should display an error message if there was data and the second time no data is provided', () => {
  const node = shallow(
    <Chart report={{...report, result: {data: {foo: 123}}}} errorMessage="Error" />
  );

  node.setProps({report: {...report, result: null}});

  expect(node.find('ReportBlankSlate').prop('errorMessage')).toBe('Error');
});

it('should use the special targetLine type when target values are enabled on a line chart', () => {
  const targetValue = {targetValue: {active: true, countChart: {isBelow: true, value: 1}}};

  shallow(
    <Chart
      report={{
        ...report,
        result: {data: {foo: 123}},
        data: {...report.data, visualization: 'line', configuration: targetValue}
      }}
    />
  );

  expect(createTargetLineConfig).toHaveBeenCalled();
});

it('should render combined chart if report is combined', () => {
  shallow(
    <Chart
      report={{
        ...report,
        result: {data: null},
        combined: true
      }}
    />
  );

  expect(createCombinedChartConfig).toHaveBeenCalled();
});

it('should render default normal chart if report is a single report', () => {
  shallow(
    <Chart
      report={{
        ...report,
        result: {data: {}}
      }}
    />
  );

  expect(createDefaultChartConfig).toHaveBeenCalled();
});
