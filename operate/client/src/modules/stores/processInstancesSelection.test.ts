/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstancesSelectionStore} from './processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';

describe('ProcessInstancesSelection - checkedProcessInstanceIds', () => {
  beforeEach(() => {
    processInstancesSelectionStore.reset();
    processInstancesStore.setProcessInstances({
      filteredProcessInstancesCount: 4,
      processInstances: [
        {
          id: '1',
          state: 'ACTIVE',
          processId: '',
          processName: '',
          processVersion: 0,
          startDate: '',
          endDate: null,
          bpmnProcessId: '',
          hasActiveOperation: false,
          operations: [],
          sortValues: [],
          parentInstanceId: null,
          rootInstanceId: null,
          callHierarchy: [],
          tenantId: '',
        },
        {
          id: '2',
          state: 'COMPLETED',
          processId: '',
          processName: '',
          processVersion: 0,
          startDate: '',
          endDate: null,
          bpmnProcessId: '',
          hasActiveOperation: false,
          operations: [],
          sortValues: [],
          parentInstanceId: null,
          rootInstanceId: null,
          callHierarchy: [],
          tenantId: '',
        },
        {
          id: '3',
          state: 'ACTIVE',
          processId: '',
          processName: '',
          processVersion: 0,
          startDate: '',
          endDate: null,
          bpmnProcessId: '',
          hasActiveOperation: false,
          operations: [],
          sortValues: [],
          parentInstanceId: null,
          rootInstanceId: null,
          callHierarchy: [],
          tenantId: '',
        },
        {
          id: '4',
          state: 'INCIDENT',
          processId: '',
          processName: '',
          processVersion: 0,
          startDate: '',
          endDate: null,
          bpmnProcessId: '',
          hasActiveOperation: false,
          operations: [],
          sortValues: [],
          parentInstanceId: null,
          rootInstanceId: null,
          callHierarchy: [],
          tenantId: '',
        },
      ],
    });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should return selectedProcessInstanceIds when selectionMode is INCLUDE', () => {
    processInstancesSelectionStore.state = {
      selectedProcessInstanceIds: ['1', '3'],
      isAllChecked: false,
      selectionMode: 'INCLUDE',
    };

    const result = processInstancesSelectionStore.checkedProcessInstanceIds;
    expect(result).toEqual(['1', '3']);
  });

  it('should return all process instance IDs minus selectedProcessInstanceIds when selectionMode is EXCLUDE', () => {
    processInstancesSelectionStore.state = {
      selectedProcessInstanceIds: ['1', '3'],
      isAllChecked: false,
      selectionMode: 'EXCLUDE',
    };

    const result = processInstancesSelectionStore.checkedProcessInstanceIds;
    expect(result).toEqual(['2', '4']);
  });

  it('should return all process instance IDs when selectionMode is ALL', () => {
    processInstancesSelectionStore.state = {
      selectedProcessInstanceIds: [],
      isAllChecked: true,
      selectionMode: 'ALL',
    };

    const result = processInstancesSelectionStore.checkedProcessInstanceIds;
    expect(result).toEqual(['1', '2', '3', '4']);
  });

  it('should handle an empty processInstances array', () => {
    processInstancesStore.setProcessInstances({
      filteredProcessInstancesCount: 0,
      processInstances: [],
    });

    processInstancesSelectionStore.state = {
      selectedProcessInstanceIds: ['1', '3'],
      isAllChecked: false,
      selectionMode: 'EXCLUDE',
    };

    const result = processInstancesSelectionStore.checkedProcessInstanceIds;
    expect(result).toEqual([]);
  });

  it('should handle an empty selectedProcessInstanceIds array', () => {
    processInstancesSelectionStore.state = {
      selectedProcessInstanceIds: [],
      isAllChecked: false,
      selectionMode: 'EXCLUDE',
    };

    const result = processInstancesSelectionStore.checkedProcessInstanceIds;
    expect(result).toEqual(['1', '2', '3', '4']);
  });

  it('should handle when selectionMode is INCLUDE and selectedProcessInstanceIds is empty', () => {
    processInstancesSelectionStore.state = {
      selectedProcessInstanceIds: [],
      isAllChecked: false,
      selectionMode: 'INCLUDE',
    };

    const result = processInstancesSelectionStore.checkedProcessInstanceIds;
    expect(result).toEqual([]);
  });
});
