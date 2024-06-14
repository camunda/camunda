/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import KpiSummary from './KpiSummary';

const succeededKpi = {
  reportName: 'Duration Report',
  value: '13',
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

it('should display two indicators if one goal succeeds and the other fails', () => {
  const node = shallow(<KpiSummary kpis={[succeededKpi, failedKpi]} />);

  expect(node.find('.success').length).toBe(1);
  expect(node.find('.error').length).toBe(1);
});

it('should display single indicator if all goals succeeded or failed', () => {
  const node = shallow(<KpiSummary kpis={[succeededKpi, {...failedKpi, isBelow: false}]} />);

  expect(node.find('.success').length).toBe(1);
  expect(node.find('.error')).not.toExist();
});
