/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstancesSelectionStore} from './instancesSelection';

describe('InstancesSelection - checkedIds', () => {
  beforeEach(() => {
    processInstancesSelectionStore.reset();
    processInstancesSelectionStore.setRuntime({
      totalCount: 4,
      visibleIds: ['1', '2', '3', '4'],
      visibleRunningIds: ['1', '3', '4'],
      visibleFinishedIds: ['2'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should return selectedIds when selectionMode is INCLUDE', () => {
    processInstancesSelectionStore.state = {
      selectedIds: ['1', '3'],
      selectionMode: 'INCLUDE',
    };

    const result = processInstancesSelectionStore.checkedIds;
    expect(result).toEqual(['1', '3']);
  });

  it('should return all process instance IDs minus selectedIds when selectionMode is EXCLUDE', () => {
    processInstancesSelectionStore.state = {
      selectedIds: ['1', '3'],
      selectionMode: 'EXCLUDE',
    };

    const result = processInstancesSelectionStore.checkedIds;
    expect(result).toEqual(['2', '4']);
  });

  it('should return all process instance IDs when selectionMode is ALL', () => {
    processInstancesSelectionStore.state = {
      selectedIds: [],
      selectionMode: 'ALL',
    };

    const result = processInstancesSelectionStore.checkedIds;
    expect(result).toEqual(['1', '2', '3', '4']);
  });

  it('should handle an empty processInstances array', () => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 0,
      visibleIds: [],
      visibleRunningIds: [],
      visibleFinishedIds: [],
    });

    processInstancesSelectionStore.state = {
      selectedIds: ['1', '3'],
      selectionMode: 'EXCLUDE',
    };

    const result = processInstancesSelectionStore.checkedIds;
    expect(result).toEqual([]);
  });

  it('should handle an empty selectedIds array', () => {
    processInstancesSelectionStore.state = {
      selectedIds: [],
      selectionMode: 'EXCLUDE',
    };

    const result = processInstancesSelectionStore.checkedIds;
    expect(result).toEqual(['1', '2', '3', '4']);
  });

  it('should handle when selectionMode is INCLUDE and selectedIds is empty', () => {
    processInstancesSelectionStore.state = {
      selectedIds: [],
      selectionMode: 'INCLUDE',
    };

    const result = processInstancesSelectionStore.checkedIds;
    expect(result).toEqual([]);
  });
});

describe('InstancesSelection - hasSelectedFinishedInstances', () => {
  beforeEach(() => {
    processInstancesSelectionStore.reset();
    processInstancesSelectionStore.setRuntime({
      totalCount: 5,
      visibleIds: ['1', '2', '3', '4', '5'],
      visibleRunningIds: ['1', '3', '4'],
      visibleFinishedIds: ['2', '5'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should return true when finished instances are selected in INCLUDE mode', () => {
    processInstancesSelectionStore.state = {
      selectedIds: ['2', '3'],
      selectionMode: 'INCLUDE',
    };

    expect(processInstancesSelectionStore.hasSelectedFinishedInstances).toBe(
      true,
    );
  });

  it('should return false when no finished instances are selected in INCLUDE mode', () => {
    processInstancesSelectionStore.state = {
      selectedIds: ['1', '3', '4'],
      selectionMode: 'INCLUDE',
    };

    expect(processInstancesSelectionStore.hasSelectedFinishedInstances).toBe(
      false,
    );
  });

  it('should return true when selectionMode is ALL', () => {
    processInstancesSelectionStore.state = {
      selectedIds: [],
      selectionMode: 'ALL',
    };

    expect(processInstancesSelectionStore.hasSelectedFinishedInstances).toBe(
      true,
    );
  });

  it('should return true when selectionMode is EXCLUDE', () => {
    processInstancesSelectionStore.state = {
      selectedIds: ['1', '3'],
      selectionMode: 'EXCLUDE',
    };

    expect(processInstancesSelectionStore.hasSelectedFinishedInstances).toBe(
      true,
    );
  });
});
