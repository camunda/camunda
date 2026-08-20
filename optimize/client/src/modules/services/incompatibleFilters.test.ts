/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {incompatibleFilters} from './incompatibleFilters';

it('should return true if filters contains completedInstancesOnly and runningInstancesOnly together', () => {
  expect(
    incompatibleFilters([{type: 'completedInstancesOnly'}, {type: 'runningInstancesOnly'}], {})
  ).toBe(true);
});

it('should return true if filters contains endDate and runningInstancesOnly together', () => {
  expect(incompatibleFilters([{type: 'instanceEndDate'}, {type: 'runningInstancesOnly'}], {})).toBe(
    true
  );
});

it('should return false if filters contains only completedInstancesOnly', () => {
  expect(incompatibleFilters([{type: 'completedInstancesOnly'}], {})).toBe(false);
});

it('should only be compatible if flow node status filters filters are in flow node view', () => {
  expect(
    incompatibleFilters([{type: 'completedFlowNodesOnly'}, {type: 'runningFlowNodesOnly'}], {
      entity: 'instance',
    })
  ).toBe(false);

  expect(
    incompatibleFilters([{type: 'completedFlowNodesOnly'}, {type: 'runningFlowNodesOnly'}], {
      entity: 'flowNode',
    })
  ).toBe(true);
});

it('should return true if filters contains open and resolved incidents on the view level for incident reports', () => {
  expect(
    incompatibleFilters(
      [
        {type: 'includesOpenIncident', filterLevel: 'view'},
        {type: 'includesResolvedIncident', filterLevel: 'view'},
      ],
      {entity: 'incident'}
    )
  ).toBe(true);
});

it('should return true if filters contains without incidents and open/resolved incidents on any level', () => {
  expect(
    incompatibleFilters([{type: 'doesNotIncludeIncident'}, {type: 'includesResolvedIncident'}])
  ).toBe(true);
});
