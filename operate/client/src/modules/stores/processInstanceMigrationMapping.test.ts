/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstanceMigrationMappingStore} from './processInstanceMigrationMapping';

jest.mock('modules/stores/processes/processes.migration', () => ({
  processesStore: {
    migrationState: {selectedTargetProcess: {bpmnProcessId: 'orderProcess'}},
    getSelectedProcessDetails: () => ({bpmnProcessId: 'orderProcess'}),
  },
}));

describe('processInstanceMigrationMappingStore', () => {
  afterEach(() => {
    processInstanceMigrationMappingStore.reset();
  });

  it('should toggle mapped filter', () => {
    expect(
      processInstanceMigrationMappingStore.state.isMappedFilterEnabled,
    ).toBe(false);

    processInstanceMigrationMappingStore.toggleMappedFilter();
    expect(
      processInstanceMigrationMappingStore.state.isMappedFilterEnabled,
    ).toBe(true);

    processInstanceMigrationMappingStore.toggleMappedFilter();
    expect(
      processInstanceMigrationMappingStore.state.isMappedFilterEnabled,
    ).toBe(false);

    processInstanceMigrationMappingStore.toggleMappedFilter();
    expect(
      processInstanceMigrationMappingStore.state.isMappedFilterEnabled,
    ).toBe(true);

    processInstanceMigrationMappingStore.reset();
    expect(
      processInstanceMigrationMappingStore.state.isMappedFilterEnabled,
    ).toBe(false);
  });
});
