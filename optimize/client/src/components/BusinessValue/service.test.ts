/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get} from 'request';

import {BUSINESS_VALUE_FIXTURE} from './fixtures';
import {loadSummary} from './service';
import type {BusinessValueFilter} from './types';

const mockIsBusinessValueMockEnabled = jest.fn<Promise<boolean>, []>();

jest.mock('config', () => ({
  isBusinessValueMockEnabled: () => mockIsBusinessValueMockEnabled(),
}));

jest.mock('request');

const mockedGet = jest.mocked(get);

describe('loadSummary', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockIsBusinessValueMockEnabled.mockResolvedValue(false);
  });

  it('should load business value summary with correct filter', async () => {
    const json = jest.fn(async () => BUSINESS_VALUE_FIXTURE);
    mockedGet.mockResolvedValue({json} as unknown as Response);

    const filter: BusinessValueFilter = {
      startDate: '2025-09-01',
      endDate: '2026-01-31',
      tenantId: 'tenant123',
    };

    const summary = await loadSummary(filter);

    expect(mockedGet).toHaveBeenCalledWith('api/business-value/summary', {
      startDate: '2025-09-01',
      endDate: '2026-01-31',
      tenantId: 'tenant123',
    });
    expect(summary).toEqual(BUSINESS_VALUE_FIXTURE);
  });

  it('should use the mock service when the ui config flag is enabled', async () => {
    mockIsBusinessValueMockEnabled.mockResolvedValue(true);

    const filter: BusinessValueFilter = {
      startDate: '2025-09-01',
      endDate: '2026-01-31',
    };

    await expect(loadSummary(filter)).resolves.toEqual(BUSINESS_VALUE_FIXTURE);
    expect(mockIsBusinessValueMockEnabled).toHaveBeenCalled();
    expect(mockedGet).not.toHaveBeenCalled();
  });

  it('should load business value summary without a filter', async () => {
    const json = jest.fn(async () => BUSINESS_VALUE_FIXTURE);
    mockedGet.mockResolvedValue({json} as unknown as Response);

    const summary = await loadSummary();

    expect(mockedGet).toHaveBeenCalledWith('api/business-value/summary');
    expect(summary).toEqual(BUSINESS_VALUE_FIXTURE);
  });
});
