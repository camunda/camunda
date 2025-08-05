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
    expect(batchModificationStore.state.selectedTargetItemId).toBeNull();

    batchModificationStore.enable();
    expect(batchModificationStore.state.selectedTargetItemId).toBeNull();

    batchModificationStore.selectTargetItem('123');
    expect(batchModificationStore.state.selectedTargetItemId).toBe('123');

    batchModificationStore.selectTargetItem('456');
    expect(batchModificationStore.state.selectedTargetItemId).toBe('456');

    batchModificationStore.selectTargetItem(null);
    expect(batchModificationStore.state.selectedTargetItemId).toBeNull();

    batchModificationStore.selectTargetItem('123');
    batchModificationStore.reset();
    expect(batchModificationStore.state.selectedTargetItemId).toBeNull();
  });
});
