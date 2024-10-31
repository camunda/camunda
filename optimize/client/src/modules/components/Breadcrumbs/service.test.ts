/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getEntityId} from './service';

describe('getEntityId', () => {
  it('should return the entity ID when type is "collection" and ID is present in path', () => {
    const path = '/collection/12345';
    const result = getEntityId('collection', path);
    expect(result).toBe('12345');
  });

  it('should return the entity ID when type is "report" and ID is present in path', () => {
    const path = '/report/67890';
    const result = getEntityId('report', path);
    expect(result).toBe('67890');
  });

  it('should return the entity ID when type is "dashboard" and ID is present in path', () => {
    const path = '/dashboard/abcde';
    const result = getEntityId('dashboard', path);
    expect(result).toBe('abcde');
  });

  it('should return null when the entity ID is "new"', () => {
    const path = '/dashboard/new';
    const result = getEntityId('dashboard', path);
    expect(result).toBeNull();
  });

  it('should return undefined if the type is not in the path', () => {
    const path = '/other/12345';
    const result = getEntityId('collection', path);
    expect(result).toBeUndefined();
  });

  it('should return undefined if path has a type but no ID', () => {
    const path = '/dashboard/';
    const result = getEntityId('dashboard', path);
    expect(result).toBeUndefined();
  });

  it('should handle complex paths and extracts the correct ID', () => {
    const path = '/collection/12345/dashboard/67890/report/abcde';
    const resultCollection = getEntityId('collection', path);
    const resultDashboard = getEntityId('dashboard', path);
    const resultReport = getEntityId('report', path);

    expect(resultCollection).toBe('12345');
    expect(resultDashboard).toBe('67890');
    expect(resultReport).toBe('abcde');
  });
});
