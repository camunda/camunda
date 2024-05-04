/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from '@testing-library/react';
import {mockProcessInstances} from 'modules/testUtils';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockProcessStatisticsWithActiveAndIncidents} from 'modules/mocks/mockProcessStatistics';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {processStatisticsBatchModificationStore} from './processStatistics.batchModification';
import {processInstancesStore} from '../processInstances';

describe('stores/processStatistics.batchModification', () => {
  beforeEach(async () => {
    const processInstance = mockProcessInstances.processInstances[0]!;

    mockFetchProcessInstances().withSuccess({
      processInstances: [processInstance],
      totalCount: 1,
    });
    processInstancesStore.init();
    processInstancesStore.fetchProcessInstancesFromFilters();
    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched'),
    );

    mockFetchProcessInstancesStatistics().withSuccess(
      mockProcessStatisticsWithActiveAndIncidents,
    );
    processStatisticsBatchModificationStore.fetchProcessStatistics();
    await waitFor(() =>
      expect(processStatisticsBatchModificationStore.state.status).toBe(
        'fetched',
      ),
    );
  });

  afterEach(() => {
    processStatisticsBatchModificationStore.reset();
    processInstancesStore.reset();
  });

  it('should get instances count', async () => {
    expect(
      processStatisticsBatchModificationStore.getInstancesCount('userTask'),
    ).toBe(4);
    expect(
      processStatisticsBatchModificationStore.getInstancesCount('endEvent'),
    ).toBe(8);
    expect(
      processStatisticsBatchModificationStore.getInstancesCount('startEvent'),
    ).toBe(0);

    processStatisticsBatchModificationStore.reset();

    expect(
      processStatisticsBatchModificationStore.getInstancesCount('userTask'),
    ).toBe(0);
    expect(
      processStatisticsBatchModificationStore.getInstancesCount('endEvent'),
    ).toBe(0);
    expect(
      processStatisticsBatchModificationStore.getInstancesCount('startEvent'),
    ).toBe(0);
  });

  it('should get overlays data', async () => {
    expect(
      processStatisticsBatchModificationStore.getOverlaysData({
        sourceFlowNodeId: 'userTask',
        targetFlowNodeId: 'startEvent',
      }),
    ).toEqual([
      {
        flowNodeId: 'userTask',
        payload: {cancelledTokenCount: 4},
        position: {right: -7, top: -14},
        type: 'batchModificationsBadge',
      },
      {
        flowNodeId: 'startEvent',
        payload: {newTokenCount: 4},
        position: {right: -7, top: -14},
        type: 'batchModificationsBadge',
      },
    ]);

    expect(
      processStatisticsBatchModificationStore.getOverlaysData({
        sourceFlowNodeId: 'startEvent',
        targetFlowNodeId: 'endEvent',
      }),
    ).toEqual([
      {
        flowNodeId: 'startEvent',
        payload: {cancelledTokenCount: 0},
        position: {right: -7, top: -14},
        type: 'batchModificationsBadge',
      },
      {
        flowNodeId: 'endEvent',
        payload: {newTokenCount: 0},
        position: {right: -7, top: -14},
        type: 'batchModificationsBadge',
      },
    ]);

    expect(
      processStatisticsBatchModificationStore.getOverlaysData({
        sourceFlowNodeId: 'endEvent',
        targetFlowNodeId: 'userTask',
      }),
    ).toEqual([
      {
        flowNodeId: 'endEvent',
        payload: {cancelledTokenCount: 8},
        position: {right: -7, top: -14},
        type: 'batchModificationsBadge',
      },
      {
        flowNodeId: 'userTask',
        payload: {newTokenCount: 8},
        position: {right: -7, top: -14},
        type: 'batchModificationsBadge',
      },
    ]);
  });
});
