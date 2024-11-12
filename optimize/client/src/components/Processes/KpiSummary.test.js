/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

  expect(node.dive().find('.success').length).toBe(1);
  expect(node.dive().find('.error').length).toBe(1);
});

it('should display single indicator if all goals succeeded or failed', () => {
  const node = shallow(<KpiSummary kpis={[succeededKpi, {...failedKpi, isBelow: false}]} />);

  expect(node.dive().find('.success').length).toBe(1);
  expect(node.dive().find('.error')).not.toExist();
});
