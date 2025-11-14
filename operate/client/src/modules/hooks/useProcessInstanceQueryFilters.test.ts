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
  it('should correctly map filters to request including processDefinitionVersionTag', () => {
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

  it('should include processDefinitionVersionTag when version is provided', () => {
    const mockFilters: ProcessInstanceFilters = {
      version: 'v2.5',
      completed: true,
    };

    mockedUseFilters.mockReturnValue({
      getFilters: () => mockFilters,
      setFilters: vi.fn(),
      areProcessInstanceStatesApplied: vi.fn(),
      areDecisionInstanceStatesApplied: vi.fn(),
    });

    const {result} = renderHook(() => useProcessInstanceQueryFilters());

    expect(result.current.filter).toHaveProperty(
      'processDefinitionVersionTag',
      'v2.5',
    );
    expect(result.current.filter).toEqual({
      processDefinitionVersionTag: 'v2.5',
      state: {
        $eq: 'COMPLETED',
      },
    });
  });

  it('should handle filters without version', () => {
    const mockFilters: ProcessInstanceFilters = {
      active: true,
      tenant: 'tenant1',
    };

    mockedUseFilters.mockReturnValue({
      getFilters: () => mockFilters,
      setFilters: vi.fn(),
      areProcessInstanceStatesApplied: vi.fn(),
      areDecisionInstanceStatesApplied: vi.fn(),
    });

    const {result} = renderHook(() => useProcessInstanceQueryFilters());

    expect(result.current.filter).toEqual({
      tenantId: {
        $eq: 'tenant1',
      },
      state: {
        $eq: 'ACTIVE',
      },
    });
    expect(result.current.filter).not.toHaveProperty(
      'processDefinitionVersionTag',
    );
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
