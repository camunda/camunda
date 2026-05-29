/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import React from 'react';
import {useElementInstancesSearchPaginated} from './useElementInstancesSearchPaginated';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import type {QueryElementInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';

const getWrapper = () => {
  const queryClient = getMockQueryClient();
  const Wrapper = ({children}: {children: React.ReactNode}) =>
    React.createElement(QueryClientProvider, {client: queryClient}, children);
  return Wrapper;
};

const createMockElementInstance = (key: string) => ({
  elementInstanceKey: key,
  elementId: 'task1',
  elementName: 'Task 1',
  type: 'SERVICE_TASK' as const,
  state: 'ACTIVE' as const,
  startDate: '2023-01-01T10:00:00.000Z',
  processDefinitionKey: '2',
  processDefinitionId: 'test-process',
  processInstanceKey: '123',
  hasIncident: false,
  tenantId: '<default>',
  endDate: null,
  rootProcessInstanceKey: null,
  incidentKey: null,
});

const TEST_PAYLOAD: QueryElementInstancesRequestBody = {
  filter: {processInstanceKey: '123'},
  sort: [{field: 'startDate', order: 'asc'}],
};

describe('useElementInstancesSearchPaginated', () => {
  it('fetches the first page of results on mount', async () => {
    mockSearchElementInstances().withSuccess({
      items: [createMockElementInstance('1'), createMockElementInstance('2')],
      page: {
        totalItems: 2,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    const {result} = renderHook(
      () => useElementInstancesSearchPaginated({payload: TEST_PAYLOAD}),
      {wrapper: getWrapper()},
    );

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.data?.pages[0]?.items).toHaveLength(2);
  });

  it('does not fetch when enabled is false', async () => {
    mockSearchElementInstances().withSuccess({
      items: [createMockElementInstance('1')],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    const {result} = renderHook(
      () =>
        useElementInstancesSearchPaginated({
          payload: TEST_PAYLOAD,
          enabled: false,
        }),
      {wrapper: getWrapper()},
    );

    expect(result.current.status).toBe('pending');
    expect(result.current.isFetched).toBe(false);
    expect(result.current.data).toBeUndefined();
  });

  it('has no next page when total items fit on the first page', async () => {
    mockSearchElementInstances().withSuccess({
      items: [createMockElementInstance('1'), createMockElementInstance('2')],
      page: {
        totalItems: 2,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    const {result} = renderHook(
      () => useElementInstancesSearchPaginated({payload: TEST_PAYLOAD}),
      {wrapper: getWrapper()},
    );

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.hasNextPage).toBe(false);
  });

  it('has a next page when there are more items than PAGE_LIMIT', async () => {
    const items = Array.from({length: 50}, (_, i) =>
      createMockElementInstance(String(i + 1)),
    );

    mockSearchElementInstances().withSuccess({
      items,
      page: {
        totalItems: 100,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: true,
      },
    });

    const {result} = renderHook(
      () => useElementInstancesSearchPaginated({payload: TEST_PAYLOAD}),
      {wrapper: getWrapper()},
    );

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.hasNextPage).toBe(true);
  });
});
