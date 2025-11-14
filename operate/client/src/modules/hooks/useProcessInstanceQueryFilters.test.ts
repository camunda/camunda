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
import {useProcessInstanceQueryFilters} from './useProcessInstanceQueryFilters';

vi.mock('modules/hooks/useFilters');

const mockedUseFilters = vi.mocked(useFilters);

describe('useProcessInstanceQueryFilters', () => {
  it('should correctly map filters to request', () => {
    const mockFilters: ProcessInstanceFilters = {
      version: 'v1.0',
      active: true,
      tenant: 'tenant1',
      ids: 'id1,id2',
    };

    mockedUseFilters.mockReturnValue({
      getFilters: () => mockFilters,
      setFilters: vi.fn(),
      areProcessInstanceStatesApplied: vi.fn(),
      areDecisionInstanceStatesApplied: vi.fn(),
    });

    const {result} = renderHook(() => useProcessInstanceQueryFilters());

    expect(result.current.filter).toEqual({
      processDefinitionVersionTag: 'v1.0',
      processInstanceKey: {
        $in: ['id1', 'id2'],
      },
      tenantId: {
        $eq: 'tenant1',
      },
      state: {
        $eq: 'ACTIVE',
      },
    });
  });

  it('should correctly map all filters', () => {
    const mockFilters: ProcessInstanceFilters = {
      startDateAfter: '2023-01-01',
      startDateBefore: '2023-01-31',
      endDateAfter: '2023-02-01',
      endDateBefore: '2023-02-28',
      process: 'process1',
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
      version: 'v1.0',
    };

    mockedUseFilters.mockReturnValue({
      getFilters: () => mockFilters,
      setFilters: vi.fn(),
      areProcessInstanceStatesApplied: vi.fn(),
      areDecisionInstanceStatesApplied: vi.fn(),
    });

    const {result} = renderHook(() => useProcessInstanceQueryFilters());

    expect(result.current.filter).toEqual({
      processDefinitionVersionTag: 'v1.0',
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
    });
    expect(result.current.filter).toHaveProperty(
      'processDefinitionVersionTag',
      'v1.0',
    );
  });

  it('should map filters correctly with only state', () => {
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

    const {result} = renderHook(() => useProcessInstanceQueryFilters());

    expect(result.current.filter).toEqual({
      state: {
        $in: ['ACTIVE', 'COMPLETED', 'TERMINATED'],
      },
    });
  });

  it('should handle empty filters', () => {
    const mockFilters: ProcessInstanceFilters = {};

    mockedUseFilters.mockReturnValue({
      getFilters: () => mockFilters,
      setFilters: vi.fn(),
      areProcessInstanceStatesApplied: vi.fn(),
      areDecisionInstanceStatesApplied: vi.fn(),
    });

    const {result} = renderHook(() => useProcessInstanceQueryFilters());

    expect(result.current).toEqual({
      filter: {},
    });
  });
});
