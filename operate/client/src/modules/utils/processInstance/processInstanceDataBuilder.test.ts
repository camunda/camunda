/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {buildV2ProcessInstanceData} from './processInstanceDataBuilder';

const v2Instance: ProcessInstance = {
  processInstanceKey: '123',
  processDefinitionKey: '456',
  processDefinitionName: 'Order Process',
  processDefinitionVersion: 3,
  processDefinitionId: 'order-process',
  state: 'ACTIVE',
  startDate: '2023-01-01T10:00:00Z',
  endDate: '2023-01-02T10:00:00Z',
  tenantId: 'tenant-1',
  parentProcessInstanceKey: 'parent-123',
  hasIncident: false,
};

describe('buildV2ProcessInstanceData', () => {
  it('should map V2 fields to V1 format correctly', () => {
    const result = buildV2ProcessInstanceData(v2Instance);

    expect(result).toEqual({
      id: '123',
      processId: '456',
      processName: 'Order Process',
      processVersion: 3,
      state: 'ACTIVE',
      startDate: '2023-01-01T10:00:00Z',
      endDate: '2023-01-02T10:00:00Z',
      tenantId: 'tenant-1',
      parentInstanceId: 'parent-123',
      bpmnProcessId: 'order-process',
      hasActiveOperation: false,
      operations: [],
      sortValues: [],
      rootInstanceId: null,
      callHierarchy: [],
      permissions: undefined,
    });
  });

  it('should convert hasIncident to INCIDENT state', () => {
    const v2InstanceWithIncident: ProcessInstance = {
      ...v2Instance,
      hasIncident: true,
      state: 'ACTIVE',
    };

    const result = buildV2ProcessInstanceData(v2InstanceWithIncident);

    expect(result.state).toBe('INCIDENT');
  });
});
