/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {useDeleteResource} from './useDeleteResource';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {http, HttpResponse} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import type {ReactNode} from 'react';
import {mockDeleteResource} from 'modules/mocks/api/v2/resource/deleteResource';
import type {DeleteResourceRequestBody} from '@camunda/camunda-api-zod-schemas/8.9';

const resourceKey = '2251799813685249';
const requestBody: DeleteResourceRequestBody = {
  operationReference: 123456789,
  deleteHistory: true,
};

const createWrapper = () => {
  const queryClient = getMockQueryClient();
  return ({children}: {children: ReactNode}) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
};

describe('useDeleteResource', () => {
  it('should successfully delete resource', async () => {
    mockDeleteResource().withSuccess({}, {expectPolling: false});

    const {result} = renderHook(
      () => useDeleteResource(resourceKey, requestBody),
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

  it('should fail when delete API returns error', async () => {
    mockDeleteResource().withServerError(500);

    const {result} = renderHook(
      () => useDeleteResource(resourceKey, requestBody),
      {
        wrapper: createWrapper(),
      },
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.response?.statusText).toBe(
      'Internal Server Error',
    );
  });

  it('should call onSuccess callback when deletion succeeds', async () => {
    const onSuccess = vi.fn();

    mockDeleteResource().withSuccess({}, {expectPolling: false});

    const {result} = renderHook(
      () =>
        useDeleteResource(resourceKey, requestBody, {
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
      http.post(`/v2/resources/${resourceKey}/deletion`, () => {
        return new HttpResponse(null, {
          status: 403,
          statusText: 'Forbidden',
        });
      }),
    );

    const {result} = renderHook(
      () =>
        useDeleteResource(resourceKey, requestBody, {
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
    expect(onError.mock.calls[0][0].response?.statusText).toBe('Forbidden');
  });

  it('should show pending state during deletion', async () => {
    mockDeleteResource().withDelay({});

    const {result} = renderHook(
      () => useDeleteResource(resourceKey, requestBody),
      {
        wrapper: createWrapper(),
      },
    );

    expect(result.current.isPending).toBe(false);

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.isPending).toBe(false);
  });

  it('should handle deletion with deleteHistory false', async () => {
    const requestBodyNoHistory: DeleteResourceRequestBody = {
      operationReference: 123456789,
      deleteHistory: false,
    };

    mockDeleteResource().withSuccess({}, {expectPolling: false});

    const {result} = renderHook(
      () => useDeleteResource(resourceKey, requestBodyNoHistory),
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

  it('should handle deletion without request body', async () => {
    mockDeleteResource().withSuccess({}, {expectPolling: false});

    const {result} = renderHook(
      () => useDeleteResource(resourceKey, {} as DeleteResourceRequestBody),
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

  it('should handle network errors gracefully', async () => {
    mockDeleteResource().withNetworkError();

    const {result} = renderHook(
      () => useDeleteResource(resourceKey, requestBody),
      {
        wrapper: createWrapper(),
      },
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error).toBeDefined();
  });

  it('should handle 404 not found error', async () => {
    mockServer.use(
      http.post(`/v2/resources/${resourceKey}/deletion`, () => {
        return new HttpResponse(null, {
          status: 404,
          statusText: 'Not Found',
        });
      }),
    );

    const {result} = renderHook(
      () => useDeleteResource(resourceKey, requestBody),
      {
        wrapper: createWrapper(),
      },
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error?.response?.statusText).toBe('Not Found');
  });

  it('should handle 400 bad request error', async () => {
    mockServer.use(
      http.post(`/v2/resources/${resourceKey}/deletion`, () => {
        return new HttpResponse(null, {
          status: 400,
          statusText: 'Bad Request',
        });
      }),
    );

    const {result} = renderHook(
      () => useDeleteResource(resourceKey, requestBody),
      {
        wrapper: createWrapper(),
      },
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error?.response?.statusText).toBe('Bad Request');
  });

  it('should reset mutation state after error', async () => {
    mockDeleteResource().withServerError(500);

    const {result} = renderHook(
      () => useDeleteResource(resourceKey, requestBody),
      {
        wrapper: createWrapper(),
      },
    );

    result.current.mutate();

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    result.current.reset();

    await waitFor(() => {
      expect(result.current.isError).toBe(false);
    });

    expect(result.current.error).toBeNull();
  });
});
