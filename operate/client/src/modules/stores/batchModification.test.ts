/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {batchModificationStore} from './batchModification';

describe('batchModificationStore', () => {
  it('should initialize, enable and reset', async () => {
    batchModificationStore.enable();
    expect(batchModificationStore.state.isEnabled).toBe(true);

    batchModificationStore.reset();
    expect(batchModificationStore.state.isEnabled).toBe(false);
  });

  it('should select target flow node', () => {
    expect(batchModificationStore.state.selectedTargetElementId).toBeNull();

    batchModificationStore.enable();
    expect(batchModificationStore.state.selectedTargetElementId).toBeNull();

    batchModificationStore.selectTargetElement('123');
    expect(batchModificationStore.state.selectedTargetElementId).toBe('123');

    batchModificationStore.selectTargetElement('456');
    expect(batchModificationStore.state.selectedTargetElementId).toBe('456');

    batchModificationStore.selectTargetElement(null);
    expect(batchModificationStore.state.selectedTargetElementId).toBeNull();

    batchModificationStore.selectTargetElement('123');
    batchModificationStore.reset();
    expect(batchModificationStore.state.selectedTargetElementId).toBeNull();
  });
});
