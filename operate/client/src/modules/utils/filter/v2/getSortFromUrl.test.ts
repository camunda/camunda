/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getSortFromUrl} from './getSortFromUrl';

function createSortUrl(field: string, order: string): string {
  const params = new URLSearchParams();
  params.set('sort', `${field}+${order}`);
  return `?${params.toString()}`;
}

describe('getSortFromUrl', () => {
  it('should return default sort when no sort parameter present', () => {
    const result = getSortFromUrl('?active=true');
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should return default sort when sort parameter is empty', () => {
    const result = getSortFromUrl('?sort=');
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should handle "processInstanceKey" field name', () => {
    const result = getSortFromUrl(createSortUrl('processInstanceKey', 'asc'));
    expect(result).toEqual([{field: 'processInstanceKey', order: 'asc'}]);
  });

  it('should handle "processDefinitionName"  field name', () => {
    const result = getSortFromUrl(
      createSortUrl('processDefinitionName', 'desc'),
    );
    expect(result).toEqual([{field: 'processDefinitionName', order: 'desc'}]);
  });

  it('should handle "processDefinitionVersion" field name', () => {
    const result = getSortFromUrl(
      createSortUrl('processDefinitionVersion', 'asc'),
    );
    expect(result).toEqual([{field: 'processDefinitionVersion', order: 'asc'}]);
  });

  it('should handle "startDate" field name', () => {
    const result = getSortFromUrl(createSortUrl('startDate', 'desc'));
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should handle "tenantId"  field name', () => {
    const result = getSortFromUrl(createSortUrl('tenantId', 'desc'));
    expect(result).toEqual([{field: 'tenantId', order: 'desc'}]);
  });

  it('should handle  "parentProcessInstanceKey" ield name', () => {
    const result = getSortFromUrl(
      createSortUrl('parentProcessInstanceKey', 'asc'),
    );
    expect(result).toEqual([{field: 'parentProcessInstanceKey', order: 'asc'}]);
  });

  it('should fallback to default sort when field is invalid', () => {
    const result = getSortFromUrl(createSortUrl('invalidField', 'asc'));
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should fallback to default order when order is invalid', () => {
    const params = new URLSearchParams();
    params.set('sort', 'startDate+invalid');
    const result = getSortFromUrl(`?${params.toString()}`);
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should fallback to default when URL format is invalid', () => {
    const result = getSortFromUrl('?sort=invalid');
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should handle empty URL search string', () => {
    const result = getSortFromUrl('');
    expect(result).toEqual([{field: 'startDate', order: 'desc'}]);
  });

  it('should handle URL with multiple parameters', () => {
    const params = new URLSearchParams();
    params.set('active', 'true');
    params.set('incidents', 'true');
    params.set('sort', 'processInstanceKey+asc');
    const result = getSortFromUrl(`?${params.toString()}`);
    expect(result).toEqual([{field: 'processInstanceKey', order: 'asc'}]);
  });
});
