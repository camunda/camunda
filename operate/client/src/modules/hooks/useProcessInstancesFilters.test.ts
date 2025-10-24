/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook} from 'modules/testing-library';
import {useFilters} from 'modules/hooks/useFilters';
import {type ProcessInstanceFilters} from 'modules/utils/filter/shared';
import {useProcessInstanceFilters} from './useProcessInstancesFilters';
import {type GetProcessDefinitionStatisticsRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';

vi.mock('modules/hooks/useFilters');

const mockedUseFilters = vi.mocked(useFilters);

describe('useProcessInstanceFilters', () => {
  it('should map filters to request correctly with both state and incidents', () => {
    const mockFilters: ProcessInstanceFilters = {
      startDateAfter: '2023-01-01',
      startDateBefore: '2023-01-31',
      endDateAfter: '2023-02-01',
      endDateBefore: '2023-02-28',
      process: 'process1',
      version: '1',
      ids: 'id1,id2',
      active: true,
      incidents: true,
      completed: true,
      canceled: true,
      parentInstanceId: 'parent1',
      tenant: 'tenant1',
      retriesLeft: true,
      operationId: 'operation1',
      flowNodeId: 'flowNode1',
      errorMessage: 'some error message',
      incidentErrorHashCode: 321456,
    };

    mockedUseFilters.mockReturnValue({
      getFilters: () => mockFilters,
      setFilters: vi.fn(),
      areProcessInstanceStatesApplied: vi.fn(),
      areDecisionInstanceStatesApplied: vi.fn(),
    });

    const expectedRequest: GetProcessDefinitionStatisticsRequestBody = {
      filter: {
        startDate: {
          $gt: '2023-01-01T00:00:00.000Z',
          $lt: '2023-01-31T00:00:00.000Z',
        },
        endDate: {
          $gt: '2023-02-01T00:00:00.000Z',
          $lt: '2023-02-28T00:00:00.000Z',
        },
        processInstanceKey: {
          $in: ['id1', 'id2'],
        },
        $or: [
          {
            state: {
              $in: ['ACTIVE', 'COMPLETED', 'TERMINATED'],
            },
          },
          {hasIncident: true},
        ],
        state: {
          $in: ['ACTIVE', 'COMPLETED', 'TERMINATED'],
        },
        parentProcessInstanceKey: {
          $eq: 'parent1',
        },
        tenantId: {
          $eq: 'tenant1',
        },
        batchOperationId: {
          $eq: 'operation1',
        },
        elementId: {
          $eq: 'flowNode1',
        },
        errorMessage: {
          $in: ['some error message'],
        },
        incidentErrorHashCode: 321456,
        hasRetriesLeft: true,
      },
    };

    const {result} = renderHook(() => useProcessInstanceFilters());
    expect(result.current).toEqual(expectedRequest);
  });

  it('should map filters to request correctly with only state', () => {
    const mockFilters: ProcessInstanceFilters = {
      active: true,
      completed: true,
      canceled: true,
    };

    mockedUseFilters.mockReturnValue({
      getFilters: () => mockFilters,
      setFilters: vi.fn(),
      areProcessInstanceStatesApplied: vi.fn(),
      areDecisionInstanceStatesApplied: vi.fn(),
    });

    const expectedRequest: GetProcessDefinitionStatisticsRequestBody = {
      filter: {
        state: {
          $in: ['ACTIVE', 'COMPLETED', 'TERMINATED'],
        },
      },
    };

    const {result} = renderHook(() => useProcessInstanceFilters());
    expect(result.current).toEqual(expectedRequest);
  });

  it('should map filters to request correctly with only incidents', () => {
    const mockFilters: ProcessInstanceFilters = {
      incidents: true,
    };

    mockedUseFilters.mockReturnValue({
      getFilters: () => mockFilters,
      setFilters: vi.fn(),
      areProcessInstanceStatesApplied: vi.fn(),
      areDecisionInstanceStatesApplied: vi.fn(),
    });

    const expectedRequest: GetProcessDefinitionStatisticsRequestBody = {
      filter: {
        hasIncident: true,
      },
    };

    const {result} = renderHook(() => useProcessInstanceFilters());
    expect(result.current).toEqual(expectedRequest);
  });

  it('should handle empty filters', () => {
    const mockFilters: ProcessInstanceFilters = {};

    mockedUseFilters.mockReturnValue({
      getFilters: () => mockFilters,
      setFilters: vi.fn(),
      areProcessInstanceStatesApplied: vi.fn(),
      areDecisionInstanceStatesApplied: vi.fn(),
    });

    const expectedRequest: GetProcessDefinitionStatisticsRequestBody = {
      filter: {},
    };

    const {result} = renderHook(() => useProcessInstanceFilters());
    expect(result.current).toEqual(expectedRequest);
  });

  it('should handle partial filters', () => {
    const mockFilters: ProcessInstanceFilters = {
      startDateAfter: '2023-01-01',
      active: true,
    };

    mockedUseFilters.mockReturnValue({
      getFilters: () => mockFilters,
      setFilters: vi.fn(),
      areProcessInstanceStatesApplied: vi.fn(),
      areDecisionInstanceStatesApplied: vi.fn(),
    });

    const expectedRequest: GetProcessDefinitionStatisticsRequestBody = {
      filter: {
        startDate: {
          $gt: '2023-01-01T00:00:00.000Z',
        },
        state: {
          $in: ['ACTIVE'],
        },
      },
    };

    const {result} = renderHook(() => useProcessInstanceFilters());
    expect(result.current).toEqual(expectedRequest);
  });
});
