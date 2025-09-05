/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {buildMigrationBatchOperationFilter} from './buildMigrationBatchOperationFilter.ts';
import type {CreateMigrationBatchOperationRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';

describe('getMigrationBatchOperationFilter', () => {
  it('should map ids to $in operator', () => {
    const result = buildMigrationBatchOperationFilter({
      baseFilter: {},
      includeIds: ['123', '456', '789'],
      excludeIds: [],
    });

    expect(result).toEqual({
      processInstanceKey: {
        $in: ['123', '456', '789'],
      },
    });
  });

  it('should map excludeIds to $notIn operator', () => {
    const result = buildMigrationBatchOperationFilter({
      baseFilter: {},
      includeIds: [],
      excludeIds: ['111', '222'],
    });

    expect(result).toEqual({
      processInstanceKey: {
        $notIn: ['111', '222'],
      },
    });
  });

  it('should combine both ids and excludeIds', () => {
    const result = buildMigrationBatchOperationFilter({
      baseFilter: {},
      includeIds: ['123', '456'],
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

    const result = buildMigrationBatchOperationFilter({
      baseFilter,
      includeIds: ['123'],
      excludeIds: [],
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

    const result = buildMigrationBatchOperationFilter({
      baseFilter,
      includeIds: ['123'],
      excludeIds: ['456'],
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
    const baseFilter: CreateMigrationBatchOperationRequestBody['filter'] = {
      processInstanceKey: {
        $eq: '999',
      },
      state: {$in: ['ACTIVE']},
    };

    const result = buildMigrationBatchOperationFilter({
      baseFilter,
      includeIds: [],
      excludeIds: [],
    });

    expect(result).toEqual(baseFilter);
  });

  it('should not modify the original baseFilter object', () => {
    const baseFilter: CreateMigrationBatchOperationRequestBody['filter'] = {
      state: {$in: ['ACTIVE']},
    };
    const originalBaseFilter = {...baseFilter};

    buildMigrationBatchOperationFilter({
      baseFilter,
      includeIds: ['123'],
      excludeIds: [],
    });

    expect(baseFilter).toEqual(originalBaseFilter);
  });

  it('should handle single element arrays', () => {
    const result = buildMigrationBatchOperationFilter({
      baseFilter: {},
      includeIds: ['single-id'],
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
