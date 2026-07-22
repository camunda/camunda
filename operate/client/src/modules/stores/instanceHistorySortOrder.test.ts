/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {instanceHistorySortOrderStore} from './instanceHistorySortOrder';
import {getStateLocally, clearStateLocally} from 'modules/utils/localStorage';

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

  it('should restore the persisted order on reset', () => {
    instanceHistorySortOrderStore.toggle();
    expect(instanceHistorySortOrderStore.order).toBe('asc');

    instanceHistorySortOrderStore.reset();

    expect(instanceHistorySortOrderStore.order).toBe('asc');
  });

  it('should fall back to the default when the persisted value is invalid', () => {
    clearStateLocally();
    instanceHistorySortOrderStore.reset();

    expect(instanceHistorySortOrderStore.order).toBe('desc');
  });
});
