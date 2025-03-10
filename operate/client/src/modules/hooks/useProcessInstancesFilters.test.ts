/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook} from '@testing-library/react-hooks';
import {useFilters} from 'modules/hooks/useFilters';
import {ProcessInstanceFilters} from 'modules/utils/filter/shared';
import {ProcessInstancesStatisticsRequest} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {useProcessInstanceFilters} from './useProcessInstancesFilters';

jest.mock('modules/hooks/useFilters');

describe('useProcessInstanceFilters', () => {
  it('should map filters to request correctly', () => {
    const mockFilters: ProcessInstanceFilters = {
      startDateAfter: '2023-01-01',
      startDateBefore: '2023-01-31',
      endDateAfter: '2023-02-01',
      endDateBefore: '2023-02-28',
      process: 'process1,process2',
      ids: 'id1,id2',
      active: true,
      incidents: true,
      completed: true,
      canceled: true,
      parentInstanceId: 'parent1',
      tenant: 'tenant1',
      retriesLeft: true,
      operationId: 'operation1',
    };

    (useFilters as jest.Mock).mockReturnValue({
      getFilters: () => mockFilters,
    });

    const expectedRequest: ProcessInstancesStatisticsRequest = {
      startDate: {
        $gt: '2023-01-01',
        $lt: '2023-01-31',
      },
      endDate: {
        $gt: '2023-02-01',
        $lt: '2023-02-28',
      },
      processDefinitionKey: {
        $in: ['process1', 'process2'],
      },
      processInstanceKey: {
        $in: ['id1', 'id2'],
      },
      state: {
        $in: ['RUNNING', 'INCIDENT', 'COMPLETED', 'CANCELED'],
      },
      parentProcessInstanceKey: 'parent1',
      tenantId: 'tenant1',
      hasRetriesLeft: true,
      batchOperationKey: 'operation1',
    };

    const {result} = renderHook(() => useProcessInstanceFilters());
    expect(result.current).toEqual(expectedRequest);
  });

  it('should handle empty filters', () => {
    const mockFilters: ProcessInstanceFilters = {};

    (useFilters as jest.Mock).mockReturnValue({
      getFilters: () => mockFilters,
    });

    const expectedRequest: ProcessInstancesStatisticsRequest = {};

    const {result} = renderHook(() => useProcessInstanceFilters());
    expect(result.current).toEqual(expectedRequest);
  });

  it('should handle partial filters', () => {
    const mockFilters: ProcessInstanceFilters = {
      startDateAfter: '2023-01-01',
      active: true,
    };

    (useFilters as jest.Mock).mockReturnValue({
      getFilters: () => mockFilters,
    });

    const expectedRequest: ProcessInstancesStatisticsRequest = {
      startDate: {
        $gt: '2023-01-01',
      },
      state: {
        $in: ['RUNNING'],
      },
    };

    const {result} = renderHook(() => useProcessInstanceFilters());
    expect(result.current).toEqual(expectedRequest);
  });
});
