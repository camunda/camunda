/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstancesSelectionStore} from './processInstancesSelectionV2';

describe('ProcessInstancesSelection - checkedProcessInstanceIds', () => {
  beforeEach(() => {
    processInstancesSelectionStore.reset();
    processInstancesSelectionStore.setRuntime({
      totalProcessInstancesCount: 4,
      visibleIds: ['1', '2', '3', '4'],
      visibleRunningIds: ['1', '3', '4'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
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
    processInstancesSelectionStore.setRuntime({
      totalProcessInstancesCount: 0,
      visibleIds: [],
      visibleRunningIds: [],
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
