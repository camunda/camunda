/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {waitFor} from '@testing-library/react';
import {
  mockProcessStatistics,
  mockProcessStatisticsWithFinished,
} from 'modules/mocks/mockProcessStatistics';
import {processStatisticsStore} from './processStatistics.migration.source';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';

describe('stores/processStatistics.migration.source', () => {
  afterEach(() => {
    processStatisticsStore.reset();
  });

  it('should fetch process statistics', async () => {
    mockFetchProcessInstancesStatistics().withSuccess(
      mockProcessStatisticsWithFinished,
    );

    expect(processStatisticsStore.state.statistics).toEqual([]);

    processStatisticsStore.fetchProcessStatistics();
    await waitFor(() =>
      expect(processStatisticsStore.statistics).toEqual(mockProcessStatistics),
    );
  });
});
