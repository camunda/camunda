/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getMigrationBatchOperationFilter} from './getMigrationBatchOperationFilter';
import type {CreateMigrationBatchOperationRequestBody} from '@vzeta/camunda-api-zod-schemas/8.8';

describe('getMigrationBatchOperationFilter', () => {
  it('should return empty filter when no parameters provided', () => {
    const result = getMigrationBatchOperationFilter({});

    expect(result).toEqual({});
  });

  it('should map ids to $in operator', () => {
    const result = getMigrationBatchOperationFilter({
      ids: ['123', '456', '789'],
    });

    expect(result).toEqual({
      processInstanceKey: {
        $in: ['123', '456', '789'],
      },
    });
  });

  it('should map excludeIds to $notIn operator', () => {
    const result = getMigrationBatchOperationFilter({
      excludeIds: ['111', '222'],
    });

    expect(result).toEqual({
      processInstanceKey: {
        $notIn: ['111', '222'],
      },
    });
  });

  it('should combine both ids and excludeIds', () => {
    const result = getMigrationBatchOperationFilter({
      ids: ['123', '456'],
      excludeIds: ['789', '000'],
    });

    expect(result).toEqual({
      processInstanceKey: {
        $in: ['123', '456'],
        $notIn: ['789', '000'],
      },
    });
  });

  it('should preserve existing baseFilter properties', () => {
    const baseFilter: CreateMigrationBatchOperationRequestBody['filter'] = {
      state: {$in: ['ACTIVE']},
      tenantId: {$eq: 'tenant1'},
    };

    const result = getMigrationBatchOperationFilter({
      ids: ['123'],
      baseFilter,
    });

    expect(result).toEqual({
      state: {$in: ['ACTIVE']},
      tenantId: {$eq: 'tenant1'},
      processInstanceKey: {
        $in: ['123'],
      },
    });
  });

  it('should merge with existing processInstanceKey filter', () => {
    const baseFilter: CreateMigrationBatchOperationRequestBody['filter'] = {
      processInstanceKey: {
        $eq: '999',
      },
      state: {$in: ['ACTIVE']},
    };

    const result = getMigrationBatchOperationFilter({
      ids: ['123'],
      excludeIds: ['456'],
      baseFilter,
    });

    expect(result).toEqual({
      processInstanceKey: {
        $eq: '999',
        $in: ['123'],
        $notIn: ['456'],
      },
      state: {$in: ['ACTIVE']},
    });
  });

  it('should handle empty arrays', () => {
    const result = getMigrationBatchOperationFilter({
      ids: [],
      excludeIds: [],
    });

    expect(result).toEqual({});
  });

  it('should not modify the original baseFilter object', () => {
    const baseFilter: CreateMigrationBatchOperationRequestBody['filter'] = {
      state: {$in: ['ACTIVE']},
    };
    const originalBaseFilter = {...baseFilter};

    getMigrationBatchOperationFilter({
      ids: ['123'],
      baseFilter,
    });

    expect(baseFilter).toEqual(originalBaseFilter);
  });

  it('should handle single element arrays', () => {
    const result = getMigrationBatchOperationFilter({
      ids: ['single-id'],
      excludeIds: ['single-exclude-id'],
    });

    expect(result).toEqual({
      processInstanceKey: {
        $in: ['single-id'],
        $notIn: ['single-exclude-id'],
      },
    });
  });
});
