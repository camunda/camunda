/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
