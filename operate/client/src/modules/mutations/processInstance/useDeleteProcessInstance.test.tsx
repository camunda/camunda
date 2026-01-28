/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {useDeleteProcessInstance} from './useDeleteProcessInstance';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {http, HttpResponse} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import type {ReactNode} from 'react';
import {mockDeleteProcessInstance} from 'modules/mocks/api/v2/processInstances/deleteProcessInstance';

const processInstanceKey = '2251799813685249';

const createWrapper = () => {
  const queryClient = getMockQueryClient();
  return ({children}: {children: ReactNode}) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
};

const mockVerification404 = () => {
  mockServer.use(
    http.get(`/v2/process-instances/${processInstanceKey}`, () => {
      return new HttpResponse(null, {status: 404});
    }),
  );
};

describe('useDeleteProcessInstance', () => {
  it('should successfully delete process instance and verify deletion', async () => {
    mockDeleteProcessInstance().withSuccess(null, {expectPolling: false});
    mockVerification404();

    const {result} = renderHook(
      () => useDeleteProcessInstance(processInstanceKey),
      {
        wrapper: createWrapper(),
      },
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.isError).toBe(false);
  });

  it('should skip verification when shouldSkipResultCheck is true', async () => {
    let verificationCalled = false;

    mockDeleteProcessInstance().withSuccess(null, {expectPolling: false});

    mockServer.use(
      http.get(`/v2/process-instances/${processInstanceKey}`, () => {
        verificationCalled = true;
        return new HttpResponse(null, {status: 404});
      }),
    );

    const {result} = renderHook(
      () =>
        useDeleteProcessInstance(processInstanceKey, {
          shouldSkipResultCheck: true,
        }),
      {
        wrapper: createWrapper(),
      },
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(verificationCalled).toBe(false);
  });

  it('should fail when delete API returns error', async () => {
    mockDeleteProcessInstance().withServerError(500);

    const {result} = renderHook(
      () => useDeleteProcessInstance(processInstanceKey),
      {
        wrapper: createWrapper(),
      },
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.message).toBe('Internal Server Error');
  });

  it('should call onSuccess callback when deletion succeeds', async () => {
    const onSuccess = vi.fn();

    mockServer.use(
      http.post(`/v2/process-instances/${processInstanceKey}/deletion`, () => {
        return new HttpResponse(null, {status: 200});
      }),
    );

    mockServer.use(
      http.get(`/v2/process-instances/${processInstanceKey}`, () => {
        return new HttpResponse(null, {status: 404});
      }),
    );

    const {result} = renderHook(
      () =>
        useDeleteProcessInstance(processInstanceKey, {
          onSuccess,
        }),
      {
        wrapper: createWrapper(),
      },
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(onSuccess).toHaveBeenCalledTimes(1);
  });

  it('should call onError callback when deletion fails', async () => {
    const onError = vi.fn();

    mockServer.use(
      http.post(`/v2/process-instances/${processInstanceKey}/deletion`, () => {
        return new HttpResponse(null, {
          status: 403,
          statusText: 'Forbidden',
        });
      }),
    );

    const {result} = renderHook(
      () =>
        useDeleteProcessInstance(processInstanceKey, {
          onError,
        }),
      {
        wrapper: createWrapper(),
      },
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError.mock.calls[0][0]).toBeInstanceOf(Error);
    expect(onError.mock.calls[0][0].message).toBe('Forbidden');
  });

  it('should successfully complete deletion verification', async () => {
    mockServer.use(
      http.post(`/v2/process-instances/${processInstanceKey}/deletion`, () => {
        return new HttpResponse(null, {status: 200});
      }),
    );

    mockServer.use(
      http.get(`/v2/process-instances/${processInstanceKey}`, () => {
        return new HttpResponse(null, {status: 404});
      }),
    );

    const queryClient = getMockQueryClient();
    const wrapper = ({children}: {children: ReactNode}) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );

    const {result} = renderHook(
      () => useDeleteProcessInstance(processInstanceKey),
      {wrapper},
    );

    result.current.mutate();

    // Should successfully complete
    await waitFor(
      () => {
        expect(result.current.isSuccess).toBe(true);
      },
      {timeout: 15000},
    );

    expect(result.current.isError).toBe(false);
  });

  it('should complete deletion after cache setup', async () => {
    mockServer.use(
      http.post(`/v2/process-instances/${processInstanceKey}/deletion`, () => {
        return new HttpResponse(null, {status: 200});
      }),
    );

    mockServer.use(
      http.get(`/v2/process-instances/${processInstanceKey}`, () => {
        return new HttpResponse(null, {status: 404});
      }),
    );

    const queryClient = getMockQueryClient();
    const wrapper = ({children}: {children: ReactNode}) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );

    // Pre-populate cache with process instance
    queryClient.setQueryData(['processInstance', processInstanceKey], {
      processInstanceKey,
      state: 'ACTIVE',
    });

    const {result} = renderHook(
      () => useDeleteProcessInstance(processInstanceKey),
      {wrapper},
    );

    // Verify cache has data before mutation
    const beforeData = queryClient.getQueryData([
      'processInstance',
      processInstanceKey,
    ]);
    expect(beforeData).toBeDefined();

    result.current.mutate();

    await waitFor(
      () => {
        expect(result.current.isSuccess).toBe(true);
      },
      {timeout: 15000},
    );

    // Should not have error
    expect(result.current.isError).toBe(false);
  });

  it('should show pending state during deletion', async () => {
    mockServer.use(
      http.post(
        `/v2/process-instances/${processInstanceKey}/deletion`,
        async () => {
          await new Promise((resolve) => setTimeout(resolve, 100));
          return new HttpResponse(null, {status: 200});
        },
      ),
    );

    mockServer.use(
      http.get(`/v2/process-instances/${processInstanceKey}`, () => {
        return new HttpResponse(null, {status: 404});
      }),
    );

    const {result} = renderHook(
      () => useDeleteProcessInstance(processInstanceKey),
      {
        wrapper: createWrapper(),
      },
    );

    expect(result.current.isPending).toBe(false);

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isPending).toBe(true);
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.isPending).toBe(false);
  });
});
