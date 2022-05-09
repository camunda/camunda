/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
        {type: 'instanceEndDate', data: null},
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

it('should return true if filters contains open and resolved incidents on the view level for incident reports', () => {
  expect(
    incompatibleFilters(
      [
        {type: 'includesOpenIncident', data: null, filterLevel: 'view'},
        {type: 'includesResolvedIncident', data: null, filterLevel: 'view'},
      ],
      {entity: 'incident'}
    )
  ).toBe(true);
});

it('should return true if filters contains without incidents and open/resolved incidents on any level', () => {
  expect(
    incompatibleFilters([
      {type: 'doesNotIncludeIncident', data: null},
      {type: 'includesResolvedIncident', data: null},
    ])
  ).toBe(true);
});
