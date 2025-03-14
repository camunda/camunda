/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getInstancesCount} from './processInstances';
import {ProcessInstancesStatisticsDto} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';

describe('getInstancesCount', () => {
  it('should return the correct count of active and incident instances for a given flowNodeId', () => {
    const data: ProcessInstancesStatisticsDto[] = [
      {
        flowNodeId: 'node1',
        active: 5,
        incidents: 2,
        canceled: 0,
        completed: 0,
      },
      {
        flowNodeId: 'node2',
        active: 3,
        incidents: 1,
        canceled: 0,
        completed: 0,
      },
    ];

    expect(getInstancesCount(data, 'node1')).toBe(7);
    expect(getInstancesCount(data, 'node2')).toBe(4);
  });

  it('should return 0 if the flowNodeId is not found in the data', () => {
    const data: ProcessInstancesStatisticsDto[] = [
      {
        flowNodeId: 'node1',
        active: 5,
        incidents: 2,
        canceled: 0,
        completed: 0,
      },
    ];

    expect(getInstancesCount(data, 'node2')).toBe(0);
  });

  it('should return 0 if the data is empty', () => {
    const data: ProcessInstancesStatisticsDto[] = [];

    expect(getInstancesCount(data, 'node1')).toBe(0);
  });
});
