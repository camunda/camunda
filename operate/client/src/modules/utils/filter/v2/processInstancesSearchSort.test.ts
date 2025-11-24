/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {parseProcessInstancesSearchSort} from './processInstancesSearchSort';

function createSortParams(field: string, order: string): URLSearchParams {
  const params = new URLSearchParams();
  params.set('sort', `${field}+${order}`);
  return params;
}

describe('parseProcessInstancesSearchSort', () => {
  it('should return default sort when no sort parameter present', () => {
    const params = new URLSearchParams({active: 'true'});
    const result = parseProcessInstancesSearchSort(params);
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should return default sort when sort parameter is empty', () => {
    const params = new URLSearchParams({sort: ''});
    const result = parseProcessInstancesSearchSort(params);
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should handle "processInstanceKey" field name', () => {
    const result = parseProcessInstancesSearchSort(
      createSortParams('processInstanceKey', 'asc'),
    );
    expect(result).toEqual([{field: 'processInstanceKey', order: 'asc'}]);
  });

  it('should handle "processDefinitionName"  field name', () => {
    const result = parseProcessInstancesSearchSort(
      createSortParams('processDefinitionName', 'desc'),
    );
    expect(result).toEqual([{field: 'processDefinitionName', order: 'desc'}]);
  });

  it('should handle "processDefinitionVersion" field name', () => {
    const result = parseProcessInstancesSearchSort(
      createSortParams('processDefinitionVersion', 'asc'),
    );
    expect(result).toEqual([{field: 'processDefinitionVersion', order: 'asc'}]);
  });

  it('should handle "startDate" field name', () => {
    const result = parseProcessInstancesSearchSort(
      createSortParams('startDate', 'desc'),
    );
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should handle "tenantId"  field name', () => {
    const result = parseProcessInstancesSearchSort(
      createSortParams('tenantId', 'desc'),
    );
    expect(result).toEqual([{field: 'tenantId', order: 'desc'}]);
  });

  it('should handle  "parentProcessInstanceKey" field name', () => {
    const result = parseProcessInstancesSearchSort(
      createSortParams('parentProcessInstanceKey', 'asc'),
    );
    expect(result).toEqual([{field: 'parentProcessInstanceKey', order: 'asc'}]);
  });

  it('should fallback to default sort when field is invalid', () => {
    const result = parseProcessInstancesSearchSort(
      createSortParams('invalidField', 'asc'),
    );
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should fallback to default order when order is invalid', () => {
    const result = parseProcessInstancesSearchSort(
      createSortParams('startDate', 'invalid'),
    );
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should fallback to default when URL format is invalid', () => {
    const params = new URLSearchParams();
    params.set('sort', 'invalid');
    const result = parseProcessInstancesSearchSort(params);
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should handle empty URL search string', () => {
    const params = new URLSearchParams();
    const result = parseProcessInstancesSearchSort(params);
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should handle URL with multiple parameters', () => {
    const params = createSortParams('processInstanceKey', 'asc');
    params.set('active', 'true');
    params.set('incidents', 'true');
    const result = parseProcessInstancesSearchSort(params);
    expect(result).toEqual([{field: 'processInstanceKey', order: 'asc'}]);
  });
});
