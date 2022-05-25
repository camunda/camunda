/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Icon} from 'components';

import KpiSummary from './KpiSummary';

const succeededKpi = {
  reportName: 'Duration Report',
  value: '13123123',
  target: '312312',
  isBelow: true,
  measure: 'duration',
  type: 'time',
};

const failedKpi = {
  reportName: 'Frequency Report',
  value: '123',
  target: '100',
  isBelow: true,
  measure: 'frequency',
  type: 'quality',
};

it('should not display the summary if no goals are defined', () => {
  const node = shallow(<KpiSummary kpis={[]} />);

  expect(node.isEmptyRender()).toBe(true);
});

it('should display no data if all goals has no value', () => {
  const node = shallow(<KpiSummary kpis={[{value: null}, {value: null}]} />);

  expect(node).toIncludeText('No Data');
});

it('should display two indicators if one goal succeeds and the other fails', () => {
  const node = shallow(<KpiSummary kpis={[succeededKpi, failedKpi]} />);

  expect(node.find(Icon).length).toBe(2);
  expect(node.find(Icon).at(0).prop('className')).toBe('success');
  expect(node.find(Icon).at(1).prop('className')).toBe('error');
});

it('should display single indicator if all goals succeeded or failed', () => {
  const node = shallow(<KpiSummary kpis={[succeededKpi, {...failedKpi, isBelow: false}]} />);

  expect(node.find(Icon).length).toBe(1);
  expect(node.find(Icon).prop('className')).toBe('success');
});
