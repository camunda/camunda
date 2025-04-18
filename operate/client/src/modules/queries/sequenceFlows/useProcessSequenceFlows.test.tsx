/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {useProcessSequenceFlows} from './useProcessSequenceFlows';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {mockFetchProcessSequenceFlows} from 'modules/mocks/api/v2/flownodeInstances/sequenceFlows';

describe('useProcessSequenceFlows', () => {
  const Wrapper = ({children}: {children: React.ReactNode}) => {
    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should fetch process sequence flows successfully', async () => {
    const mockData = {
      items: [
        {
          processInstanceKey: 'processInstanceKey1',
          sequenceFlowKey: 'sequenceFlowKey1',
          processDefinitionKey: 123,
          processDefinitionId: '123',
        },
        {
          processInstanceKey: 'processInstanceKey2',
          sequenceFlowKey: 'sequenceFlowKey2',
          processDefinitionKey: 123,
          processDefinitionId: '123',
        },
      ],
    };

    mockFetchProcessSequenceFlows().withSuccess(mockData);

    const {result} = renderHook(
      () => useProcessSequenceFlows('processInstanceKey1'),
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockData);
  });

  it('should handle server error while fetching process sequence flows', async () => {
    mockFetchProcessSequenceFlows().withServerError();

    const {result} = renderHook(
      () => useProcessSequenceFlows('processInstanceKey1'),
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('failed-response');
    expect(result.current.error?.response).toBeDefined();
  });

  it('should handle network error while fetching process sequence flows', async () => {
    mockFetchProcessSequenceFlows().withNetworkError();

    const {result} = renderHook(
      () => useProcessSequenceFlows('processInstanceKey1'),
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('network-error');
    expect(result.current.error?.response).toBeNull();
  });

  it('should handle empty data', async () => {
    mockFetchProcessSequenceFlows().withSuccess({items: []});

    const {result} = renderHook(
      () => useProcessSequenceFlows('processInstanceKey1'),
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual({items: []});
  });

  it('should handle loading state', async () => {
    mockFetchProcessSequenceFlows().withDelay({items: []});

    const {result} = renderHook(
      () => useProcessSequenceFlows('processInstanceKey1'),
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() => expect(result.current.isLoading).toBe(true));
  });

  it('should not fetch data when processInstanceKey is undefined', async () => {
    mockFetchProcessSequenceFlows().withSuccess({items: []});

    const {result} = renderHook(() => useProcessSequenceFlows(undefined), {
      wrapper: Wrapper,
    });

    expect(result.current.isLoading).toBe(false);
    expect(result.current.isFetched).toBe(false);
    expect(result.current.data).toBeUndefined();
  });
});
