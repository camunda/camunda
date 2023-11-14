/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {waitFor} from '@testing-library/react';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockProcessInstances} from 'modules/testUtils';
import {mockProcessStatistics} from 'modules/mocks/mockProcessStatistics';
import {processInstancesStore} from '../processInstances';
import {processStatisticsStore} from './processStatistics.list';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';

describe('stores/processStatistics.list', () => {
  afterEach(() => {
    processStatisticsStore.reset();
    processInstancesStore.reset();
  });

  it('should fetch process statistics depending on completed operations', async () => {
    const processInstance = mockProcessInstances.processInstances[0]!;
    mockFetchProcessInstances().withSuccess({
      processInstances: [{...processInstance, hasActiveOperation: true}],
      totalCount: 1,
    });

    processStatisticsStore.init();
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched'),
    );

    expect(processStatisticsStore.state.statistics).toEqual([]);

    mockFetchProcessInstances().withSuccess({
      processInstances: [{...processInstance}],
      totalCount: 1,
    });

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);

    processStatisticsStore.fetchProcessStatistics();

    await waitFor(() =>
      expect(processStatisticsStore.state.statistics).toEqual(
        mockProcessStatistics,
      ),
    );
  });

  it('should not fetch process statistics depending on completed operations if process and version filter does not exist', async () => {
    const processInstance = mockProcessInstances.processInstances[0]!;

    mockFetchProcessInstances().withSuccess({
      processInstances: [{...processInstance, hasActiveOperation: true}],
      totalCount: 1,
    });

    processStatisticsStore.init();
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched'),
    );
    expect(processStatisticsStore.state.statistics).toEqual([]);

    mockFetchProcessInstances().withSuccess({
      processInstances: [{...processInstance}],
      totalCount: 2,
    });

    processInstancesStore.fetchProcessInstancesFromFilters();

    await waitFor(() =>
      expect(processInstancesStore.state.filteredProcessInstancesCount).toBe(2),
    );

    expect(processStatisticsStore.state.statistics).toEqual([]);
  });
});
