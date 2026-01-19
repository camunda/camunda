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

import {useIncidentProcessInstanceStatisticsByDefinition} from './useIncidentProcessInstanceStatisticsByDefinition';
import {mockFetchIncidentProcessInstanceStatisticsByDefinition} from 'modules/mocks/api/v2/incidents/fetchIncidentProcessInstanceStatisticsByDefinition';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import type {GetIncidentProcessInstanceStatisticsByDefinitionResponseBody} from '@camunda/camunda-api-zod-schemas/8.9';
import * as incidentsApi from 'modules/api/v2/incidents/fetchIncidentProcessInstanceStatisticsByDefinition';
import {isRequestError} from 'modules/request';

const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

describe('useIncidentProcessInstanceStatisticsByDefinition', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches incident statistics successfully', async () => {
    const mockResponse: GetIncidentProcessInstanceStatisticsByDefinitionResponseBody =
      {
        items: [
          {
            processDefinitionId: 'process-1',
            processDefinitionKey: 1,
            processDefinitionName: 'Process 1',
            tenantId: '',
            activeInstancesWithErrorCount: 3,
          },
        ],
        page: {
          totalItems: 1,
          startCursor: '0',
          endCursor: '0',
        },
      };

    mockFetchIncidentProcessInstanceStatisticsByDefinition().withSuccess(
      mockResponse,
    );

    const {result} = renderHook(
      () => useIncidentProcessInstanceStatisticsByDefinition(),
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockResponse);
  });

  it('returns an error when the request fails', async () => {
    mockFetchIncidentProcessInstanceStatisticsByDefinition().withServerError();

    const {result} = renderHook(
      () => useIncidentProcessInstanceStatisticsByDefinition(),
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(isRequestError(result.current.error)).toBe(true);
    if (isRequestError(result.current.error)) {
      expect(result.current.error.variant).toBe('failed-response');
    }
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

    renderHook(() => useIncidentProcessInstanceStatisticsByDefinition(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(fetchSpy).toHaveBeenCalled());

    expect(fetchSpy).toHaveBeenCalledWith({
      sort: [
        {field: 'activeInstancesWithErrorCount', order: 'desc'},
        {field: 'processDefinitionKey', order: 'desc'},
        {field: 'tenantId', order: 'desc'},
      ],
    });
  });
});
