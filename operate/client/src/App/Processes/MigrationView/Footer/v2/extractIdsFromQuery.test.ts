/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {extractIdsFromQuery} from './extractIdsFromQuery';
import type {BatchOperationQuery} from 'modules/api/processInstances/operations';

describe('extractIdsFromQuery', () => {
  it('should extract ids array from query object', () => {
    const query: BatchOperationQuery = {
      ids: ['123', '456', '789'],
    };

    const result = extractIdsFromQuery(query);

    expect(result).toEqual({
      ids: ['123', '456', '789'],
      excludeIds: undefined,
    });
  });

  it('should extract excludeIds array from query object', () => {
    const query: BatchOperationQuery = {
      excludeIds: ['111', '222'],
    };

    const result = extractIdsFromQuery(query);

    expect(result).toEqual({
      ids: undefined,
      excludeIds: ['111', '222'],
    });
  });

  it('should return undefined for missing ids property', () => {
    const query: BatchOperationQuery = {};

    const result = extractIdsFromQuery(query);

    expect(result).toEqual({
      ids: undefined,
      excludeIds: undefined,
    });
  });

  it('should return undefined for missing excludeIds property', () => {
    const query: BatchOperationQuery = {
      ids: ['123'],
    };

    const result = extractIdsFromQuery(query);

    expect(result).toEqual({
      ids: ['123'],
      excludeIds: undefined,
    });
  });

  it('should handle empty arrays', () => {
    const query: BatchOperationQuery = {
      ids: [],
      excludeIds: [],
    };

    const result = extractIdsFromQuery(query);

    expect(result).toEqual({
      ids: [],
      excludeIds: [],
    });
  });

  it('should return undefined for non-array ids value', () => {
    const query = {
      ids: 'not-an-array',
      excludeIds: ['valid-array'],
    } as unknown as BatchOperationQuery;

    const result = extractIdsFromQuery(query);

    expect(result).toEqual({
      ids: undefined,
      excludeIds: ['valid-array'],
    });
  });

  it('should return undefined for non-array excludeIds value', () => {
    const query = {
      ids: ['valid-array'],
      excludeIds: 'not-an-array',
    } as unknown as BatchOperationQuery;

    const result = extractIdsFromQuery(query);

    expect(result).toEqual({
      ids: ['valid-array'],
      excludeIds: undefined,
    });
  });

  it('should handle single element arrays', () => {
    const query: BatchOperationQuery = {
      ids: ['single-id'],
      excludeIds: ['single-exclude-id'],
    };

    const result = extractIdsFromQuery(query);

    expect(result).toEqual({
      ids: ['single-id'],
      excludeIds: ['single-exclude-id'],
    });
  });
});
