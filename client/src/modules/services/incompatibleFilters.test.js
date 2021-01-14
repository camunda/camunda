/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {incompatibleFilters} from './incompatibleFilters';

it('should return true if filters contains completedInstancesOnly and runningInstancesOnly together', () => {
  expect(
    incompatibleFilters(
      [
        {type: 'completedInstancesOnly', data: null},
        {type: 'runningInstancesOnly', data: null},
      ],
      {}
    )
  ).toBe(true);
});

it('should return true if filters contains endDate and runningInstancesOnly together', () => {
  expect(
    incompatibleFilters(
      [
        {type: 'endDate', data: null},
        {type: 'runningInstancesOnly', data: null},
      ],
      {}
    )
  ).toBe(true);
});

it('should return false if filters contains only completedInstancesOnly', () => {
  expect(incompatibleFilters([{type: 'completedInstancesOnly', data: null}], {})).toBe(false);
});

it('should only be compatible if flow node status filters filters are in flow node view', () => {
  expect(
    incompatibleFilters(
      [
        {type: 'completedFlowNodesOnly', data: null},
        {type: 'runningFlowNodesOnly', data: null},
      ],
      {entity: 'instance'}
    )
  ).toBe(false);

  expect(
    incompatibleFilters(
      [
        {type: 'completedFlowNodesOnly', data: null},
        {type: 'runningFlowNodesOnly', data: null},
      ],
      {entity: 'flowNode'}
    )
  ).toBe(true);
});
