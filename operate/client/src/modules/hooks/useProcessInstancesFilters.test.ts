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
import {processesStore} from 'modules/stores/processes/processes.list';

jest.mock('modules/hooks/useFilters');
jest.mock('modules/stores/processes/processes.list');

describe('useProcessInstanceFilters', () => {
  beforeEach(() => {
    (processesStore as any).versionsByProcessAndTenant = {
      '{process1}-{tenant1}': [
        {id: 'processId1', version: 1},
        {id: 'processId2', version: 1},
      ],
    };
  });

  it('should map filters to request correctly', () => {
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
        $in: ['processId1', 'processId2'],
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
      flowNodeId: 'flowNode1',
      errorMessage: 'some error message',
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

  it('should handle process with version "all"', () => {
    const mockFilters: ProcessInstanceFilters = {
      process: 'process1',
      version: 'all',
      tenant: 'tenant1',
    };

    (useFilters as jest.Mock).mockReturnValue({
      getFilters: () => mockFilters,
    });

    (processesStore as any).versionsByProcessAndTenant = {
      '{process1}-{tenant1}': [
        {id: 'processId1', version: 1},
        {id: 'processId2', version: 2},
      ],
    };

    const expectedRequest: ProcessInstancesStatisticsRequest = {
      processDefinitionKey: {
        $in: ['processId1', 'processId2'],
      },
      tenantId: 'tenant1',
    };

    const {result} = renderHook(() => useProcessInstanceFilters());
    expect(result.current).toEqual(expectedRequest);
  });
});
