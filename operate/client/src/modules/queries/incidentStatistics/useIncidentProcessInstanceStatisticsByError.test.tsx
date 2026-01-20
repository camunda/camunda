/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {vi} from 'vitest';
import type React from 'react';

import {useIncidentProcessInstanceStatisticsByError} from './useIncidentProcessInstanceStatisticsByError';
import {mockFetchIncidentProcessInstanceStatisticsByError} from 'modules/mocks/api/v2/incidents/fetchIncidentProcessInstanceStatisticsByError';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import type {GetIncidentProcessInstanceStatisticsByErrorResponseBody} from '@camunda/camunda-api-zod-schemas/8.9';
import * as incidentsApi from 'modules/api/v2/incidents/fetchIncidentProcessInstanceStatisticsByError';

const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

describe('useIncidentProcessInstanceStatisticsByError', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches incident statistics successfully', async () => {
    const mockResponse: GetIncidentProcessInstanceStatisticsByErrorResponseBody =
      {
        items: [
          {
            errorHashCode: 123,
            errorMessage: 'Incident message',
            activeInstancesWithErrorCount: 5,
          },
        ],
        page: {
          totalItems: 1,
          startCursor: '0',
          endCursor: '0',
        },
      };

    mockFetchIncidentProcessInstanceStatisticsByError().withSuccess(
      mockResponse,
    );

    const {result} = renderHook(
      () => useIncidentProcessInstanceStatisticsByError(),
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockResponse);
  });

  it('returns an error when the request fails', async () => {
    mockFetchIncidentProcessInstanceStatisticsByError().withServerError();

    const {result} = renderHook(
      () => useIncidentProcessInstanceStatisticsByError(),
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeInstanceOf(Error);
    expect((result.current.error as Error).message).toBeDefined();
  });

  it('applies default sorting when no payload is provided', async () => {
    const fetchSpy = vi.spyOn(incidentsApi, 'default');

    fetchSpy.mockResolvedValue({
      response: {
        items: [],
        page: {
          totalItems: 0,
          startCursor: undefined,
          endCursor: undefined,
        },
      },
      error: null,
    });

    renderHook(() => useIncidentProcessInstanceStatisticsByError(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(fetchSpy).toHaveBeenCalled());

    expect(fetchSpy).toHaveBeenCalledWith({
      sort: [
        {field: 'activeInstancesWithErrorCount', order: 'desc'},
        {field: 'errorMessage', order: 'desc'},
      ],
    });
  });
});
