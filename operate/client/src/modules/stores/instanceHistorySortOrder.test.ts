/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {instanceHistorySortOrderStore} from './instanceHistorySortOrder';
import {
  getStateLocally,
  clearStateLocally,
  storeStateLocally,
} from 'modules/utils/localStorage';

describe('stores/instanceHistorySortOrder', () => {
  beforeEach(() => {
    clearStateLocally();
    instanceHistorySortOrderStore.reset();
  });

  it('should default to latest first (desc)', () => {
    expect(instanceHistorySortOrderStore.order).toBe('desc');
  });

  it('should toggle between latest first and oldest first', () => {
    expect(instanceHistorySortOrderStore.order).toBe('desc');

    instanceHistorySortOrderStore.toggle();

    expect(instanceHistorySortOrderStore.order).toBe('asc');
    expect(getStateLocally().instanceHistorySortOrder).toBe('asc');

    instanceHistorySortOrderStore.toggle();

    expect(instanceHistorySortOrderStore.order).toBe('desc');
    expect(getStateLocally().instanceHistorySortOrder).toBe('desc');
  });

  it('should restore the default order on reset', () => {
    instanceHistorySortOrderStore.toggle();
    expect(instanceHistorySortOrderStore.order).toBe('asc');

    instanceHistorySortOrderStore.reset();

    expect(instanceHistorySortOrderStore.order).toBe('desc');
  });

  it('should fall back to the default when the persisted value is invalid', async () => {
    storeStateLocally({instanceHistorySortOrder: 'not-a-real-order'});

    vi.resetModules();
    const {instanceHistorySortOrderStore: freshStore} =
      await import('./instanceHistorySortOrder');

    expect(freshStore.order).toBe('desc');
  });
});
