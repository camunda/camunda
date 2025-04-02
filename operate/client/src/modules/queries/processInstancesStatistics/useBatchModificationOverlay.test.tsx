/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {useBatchModificationOverlayData} from './useBatchModificationOverlayData';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MODIFICATIONS} from 'modules/bpmn-js/badgePositions';
import * as filterModule from 'modules/hooks/useProcessInstancesFilters';

jest.mock('modules/hooks/useProcessInstancesFilters');
jest.mock('modules/hooks/useFilters');

describe('useBatchModificationOverlayData', () => {
  const wrapper = ({children}: {children: React.ReactNode}) => (
    <QueryClientProvider client={getMockQueryClient()}>
      {children}
    </QueryClientProvider>
  );

  beforeEach(() => {
    jest.spyOn(filterModule, 'useProcessInstanceFilters').mockReturnValue({});
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should fetch batch modification overlay data successfully', async () => {
    const mockData = {
      items: [
        {
          flowNodeId: 'messageCatchEvent',
          active: 2,
          canceled: 1,
          incidents: 3,
          completed: 4,
        },
      ],
    };
    const mockOverlayData = [
      {
        payload: {cancelledTokenCount: 5},
        type: 'batchModificationsBadge',
        flowNodeId: 'messageCatchEvent',
        position: MODIFICATIONS,
      },
      {
        payload: {newTokenCount: 5},
        type: 'batchModificationsBadge',
        flowNodeId: 'targetNode',
        position: MODIFICATIONS,
      },
    ];

    mockFetchProcessInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(
      () =>
        useBatchModificationOverlayData(
          {},
          {
            sourceFlowNodeId: 'messageCatchEvent',
            targetFlowNodeId: 'targetNode',
          },
          'process1',
        ),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockOverlayData);
  });

  it('should handle server error while fetching batch modification overlay data', async () => {
    mockFetchProcessInstancesStatistics().withServerError();

    const {result} = renderHook(
      () =>
        useBatchModificationOverlayData(
          {},
          {sourceFlowNodeId: 'sourceNode', targetFlowNodeId: 'targetNode'},
          'process1',
        ),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('failed-response');
    expect(result.current.error?.response).toBeDefined();
  });

  it('should handle network error while fetching batch modification overlay data', async () => {
    mockFetchProcessInstancesStatistics().withNetworkError();

    const {result} = renderHook(
      () =>
        useBatchModificationOverlayData(
          {},
          {sourceFlowNodeId: 'sourceNode', targetFlowNodeId: 'targetNode'},
          'process1',
        ),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('network-error');
    expect(result.current.error?.response).toBeNull();
  });

  it('should handle empty data', async () => {
    mockFetchProcessInstancesStatistics().withSuccess({
      items: [],
    });

    const {result} = renderHook(
      () =>
        useBatchModificationOverlayData(
          {},
          {sourceFlowNodeId: 'sourceNode', targetFlowNodeId: 'targetNode'},
          'process1',
        ),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual([]);
  });

  it('should handle loading state', async () => {
    const {result} = renderHook(
      () =>
        useBatchModificationOverlayData(
          {},
          {sourceFlowNodeId: 'sourceNode', targetFlowNodeId: 'targetNode'},
          'process1',
        ),
      {
        wrapper,
      },
    );

    expect(result.current.isLoading).toBe(true);
  });
});
