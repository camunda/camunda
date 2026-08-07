/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import type {ReactNode} from 'react';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSuspendProcessInstance} from 'modules/mocks/api/v2/processInstances/suspendProcessInstance';
import {mockResumeProcessInstance} from 'modules/mocks/api/v2/processInstances/resumeProcessInstance';
import {createProcessInstance} from 'modules/testUtils';
import {queryKeys} from 'modules/queries/queryKeys';
import {useSuspendProcessInstance} from './useSuspendProcessInstance';
import {useResumeProcessInstance} from './useResumeProcessInstance';

const processInstanceKey = '2251799813685294';

const createWrapper = () => {
  const queryClient = getMockQueryClient();
  const wrapper = ({children}: {children: ReactNode}) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return {queryClient, wrapper};
};

describe('useChangeProcessInstanceState', () => {
  it('should suspend a process instance and cache the SUSPENDED result once observed', async () => {
    mockSuspendProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'SUSPENDED'}),
    );

    const {queryClient, wrapper} = createWrapper();
    const {result} = renderHook(
      () => useSuspendProcessInstance(processInstanceKey),
      {wrapper},
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(
      queryClient.getQueryData(
        queryKeys.processInstance.get(processInstanceKey),
      ),
    ).toEqual(expect.objectContaining({state: 'SUSPENDED'}));
  });

  it('should resume a process instance and cache the ACTIVE result once observed', async () => {
    mockResumeProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'ACTIVE'}),
    );

    const {queryClient, wrapper} = createWrapper();
    const {result} = renderHook(
      () => useResumeProcessInstance(processInstanceKey),
      {wrapper},
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(
      queryClient.getQueryData(
        queryKeys.processInstance.get(processInstanceKey),
      ),
    ).toEqual(expect.objectContaining({state: 'ACTIVE'}));
  });

  it('should treat any non-SUSPENDED state as resumed, including a terminal state', async () => {
    mockResumeProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'COMPLETED'}),
    );

    const {queryClient, wrapper} = createWrapper();
    const {result} = renderHook(
      () => useResumeProcessInstance(processInstanceKey),
      {wrapper},
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(
      queryClient.getQueryData(
        queryKeys.processInstance.get(processInstanceKey),
      ),
    ).toEqual(expect.objectContaining({state: 'COMPLETED'}));
  });

  it('should fail without polling when the suspend request itself fails', async () => {
    const onError = vi.fn();
    let verificationRequested = false;
    mockSuspendProcessInstance().withServerError(500);
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'SUSPENDED'}),
      {
        mockResolverFn: vi.fn(() => {
          verificationRequested = true;
        }),
      },
    );

    const {wrapper} = createWrapper();
    const {result} = renderHook(
      () => useSuspendProcessInstance(processInstanceKey, {onError}),
      {wrapper},
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(onError).toHaveBeenCalledTimes(1);
    expect(verificationRequested).toBe(false);
  });

  it('should retry verification until the process instance reaches the expected state', async () => {
    mockSuspendProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'ACTIVE'}),
    );
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'SUSPENDED'}),
    );

    const {queryClient, wrapper} = createWrapper();
    const {result} = renderHook(
      () => useSuspendProcessInstance(processInstanceKey),
      {wrapper},
    );

    result.current.mutate();

    await waitFor(
      () => {
        expect(result.current.isSuccess).toBe(true);
      },
      {timeout: 5000},
    );

    expect(
      queryClient.getQueryData(
        queryKeys.processInstance.get(processInstanceKey),
      ),
    ).toEqual(expect.objectContaining({state: 'SUSPENDED'}));
  });

  it('should invalidate the process instances list cache on success', async () => {
    mockSuspendProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'SUSPENDED'}),
    );

    const {queryClient, wrapper} = createWrapper();
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const {result} = renderHook(
      () => useSuspendProcessInstance(processInstanceKey),
      {wrapper},
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(invalidateQueriesSpy).toHaveBeenCalledWith(
      expect.objectContaining({queryKey: queryKeys.processInstances.base()}),
    );
  });

  it('should call onSuccess and onError callbacks', async () => {
    const onSuccess = vi.fn();
    mockSuspendProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'SUSPENDED'}),
    );

    const {wrapper} = createWrapper();
    const {result} = renderHook(
      () => useSuspendProcessInstance(processInstanceKey, {onSuccess}),
      {wrapper},
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(onSuccess).toHaveBeenCalledTimes(1);
  });
});
