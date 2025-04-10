/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getSelectedRunningInstanceCount} from './flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';

describe('getSelectedRunningInstanceCount', () => {
  beforeEach(() => {
    flowNodeSelectionStore.reset();
    flowNodeMetaDataStore.reset();
  });

  it('should return 0 if no selection is made', () => {
    flowNodeSelectionStore.setSelection(null);
    const result = getSelectedRunningInstanceCount(10);
    expect(result).toBe(0);
  });

  it('should return 0 if the selection is a placeholder', () => {
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'someNode',
      flowNodeInstanceId: 'someInstance',
      isPlaceholder: true,
    });
    const result = getSelectedRunningInstanceCount(10);
    expect(result).toBe(0);
  });

  it('should return 0 if the root node is selected', () => {
    const result = getSelectedRunningInstanceCount(10);
    expect(result).toBe(0);
  });

  it('should return 0 if the selection has no flowNodeId', () => {
    flowNodeSelectionStore.setSelection({
      flowNodeId: undefined,
    });
    const result = getSelectedRunningInstanceCount(10);
    expect(result).toBe(0);
  });

  it('should return 0 if a specific flow node instance is selected and it is not running', () => {
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'someNode',
      flowNodeInstanceId: 'someInstance',
    });
    const result = getSelectedRunningInstanceCount(10);
    expect(result).toBe(0);
  });

  it('should handle edge cases with invalid selection', () => {
    flowNodeSelectionStore.setSelection({});
    const result = getSelectedRunningInstanceCount(10);
    expect(result).toBe(0);
  });
});
