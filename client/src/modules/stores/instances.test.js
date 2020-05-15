/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {instances} from './instances';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';

describe('stores/instances', () => {
  afterEach(() => {
    clearStateLocally();
    instances.reset();
  });

  it('should return null by default', () => {
    expect(instances.state.filteredInstancesCount).toBe(null);
  });

  // This test is skipped, because setting the local storage inside
  // the test has no effect. See https://jira.camunda.com/browse/OPE-1004
  it.skip('should return state from local storage', () => {
    instances.reset();
    storeStateLocally({filteredInstancesCount: 312});

    expect(instances.state.filteredInstancesCount).toBe(312);
  });

  it('should return store state', () => {
    instances.setInstances({filteredInstancesCount: 654});

    expect(instances.state.filteredInstancesCount).toBe(654);
  });

  it('should return store state when both is set', () => {
    storeStateLocally({filteredInstancesCount: 101});
    instances.setInstances({filteredInstancesCount: 202});

    expect(instances.state.filteredInstancesCount).toBe(202);
  });
});
