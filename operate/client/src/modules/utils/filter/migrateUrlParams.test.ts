/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  migrateUrlParams,
  PROCESS_INSTANCE_PARAM_MIGRATION,
} from './migrateUrlParams';

describe('migrateUrlParams', () => {
  it('should migrate all deprecated process instance param names', () => {
    const search = new URLSearchParams();
    search.set('process', 'myProcess');
    search.set('version', '3');
    search.set('tenant', 'tenant-A');
    search.set('ids', '123 456');
    search.set('parentInstanceId', '789');
    search.set('flowNodeId', 'task1');
    search.set('operationId', 'op-1');
    search.set('retriesLeft', 'true');
    search.set('startDateAfter', '2023-01-01');
    search.set('startDateBefore', '2023-12-31');
    search.set('endDateAfter', '2023-06-01');
    search.set('endDateBefore', '2023-06-30');

    const result = migrateUrlParams(search, PROCESS_INSTANCE_PARAM_MIGRATION);

    expect(result).not.toBeNull();
    expect(result!.get('processDefinitionId')).toBe('myProcess');
    expect(result!.get('processDefinitionVersion')).toBe('3');
    expect(result!.get('tenantId')).toBe('tenant-A');
    expect(result!.get('processInstanceKey')).toBe('123 456');
    expect(result!.get('parentProcessInstanceKey')).toBe('789');
    expect(result!.get('elementId')).toBe('task1');
    expect(result!.get('batchOperationId')).toBe('op-1');
    expect(result!.get('hasRetriesLeft')).toBe('true');
    expect(result!.get('startDateFrom')).toBe('2023-01-01');
    expect(result!.get('startDateTo')).toBe('2023-12-31');
    expect(result!.get('endDateFrom')).toBe('2023-06-01');
    expect(result!.get('endDateTo')).toBe('2023-06-30');
  });

  it('should preserve params that do not need migration', () => {
    const search = new URLSearchParams();
    search.set('active', 'true');
    search.set('incidents', 'true');
    search.set('completed', 'true');
    search.set('canceled', 'true');
    search.set('errorMessage', 'some error');
    search.set('variableName', 'myVar');
    search.set('variableValues', '42');

    const result = migrateUrlParams(search, PROCESS_INSTANCE_PARAM_MIGRATION);

    expect(result).toBeNull();
  });

  it('should migrate old params while preserving unchanged params', () => {
    const search = new URLSearchParams();
    search.set('process', 'myProcess');
    search.set('active', 'true');
    search.set('errorMessage', 'some error');

    const result = migrateUrlParams(search, PROCESS_INSTANCE_PARAM_MIGRATION);

    expect(result).not.toBeNull();
    expect(result!.get('processDefinitionId')).toBe('myProcess');
    expect(result!.get('active')).toBe('true');
    expect(result!.get('errorMessage')).toBe('some error');
    expect(result!.has('process')).toBe(false);
  });

  it('should return null when no migration is needed', () => {
    const search = new URLSearchParams();
    search.set('processDefinitionId', 'alreadyMigrated');
    search.set('active', 'true');

    const result = migrateUrlParams(search, PROCESS_INSTANCE_PARAM_MIGRATION);

    expect(result).toBeNull();
  });

  it('should return null for empty search params', () => {
    const search = new URLSearchParams();

    const result = migrateUrlParams(search, PROCESS_INSTANCE_PARAM_MIGRATION);

    expect(result).toBeNull();
  });
});
