/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {elementInstanceHistorySearchStore} from './elementInstanceHistorySearch';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import type {
  ElementInstance,
  QueryElementInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {waitFor} from '@testing-library/react';

const PROCESS_INSTANCE_KEY = '2251799813685625';

const createMockElementInstance = (
  overrides: Partial<ElementInstance> = {},
): ElementInstance => ({
  elementInstanceKey: '2251799813685630',
  elementId: 'order_task',
  elementName: 'Order Task',
  type: 'SERVICE_TASK',
  state: 'ACTIVE',
  startDate: '2023-01-01T10:00:00.000Z',
  processDefinitionKey: '2251799813685623',
  processDefinitionId: 'test-process',
  processInstanceKey: PROCESS_INSTANCE_KEY,
  hasIncident: false,
  tenantId: '<default>',
  endDate: null,
  rootProcessInstanceKey: null,
  incidentKey: null,
  ...overrides,
});

const createMockResponse = (
  items: ElementInstance[],
  totalItems: number,
): QueryElementInstancesResponseBody => ({
  items,
  page: {
    totalItems,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
});

describe('elementInstanceHistorySearchStore', () => {
  beforeEach(() => {
    vi.useFakeTimers({shouldAdvanceTime: true});
  });

  afterEach(() => {
    elementInstanceHistorySearchStore.reset();
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('initialises in idle state with no search text', () => {
    expect(elementInstanceHistorySearchStore.state.searchText).toBe('');
    expect(elementInstanceHistorySearchStore.state.status).toBe('idle');
    expect(elementInstanceHistorySearchStore.state.items).toEqual([]);
    expect(elementInstanceHistorySearchStore.hasActiveSearch).toBe(false);
  });

  it('treats whitespace-only search text as inactive', () => {
    elementInstanceHistorySearchStore.setSearchText('   ');
    expect(elementInstanceHistorySearchStore.hasActiveSearch).toBe(false);
  });

  it('debounces setSearchText: a single fetch fires after 300ms of inactivity', async () => {
    const mock = vi.fn();
    mockSearchElementInstances().withSuccess(
      createMockResponse(
        [createMockElementInstance({elementName: 'Order Task'})],
        1,
      ),
      {mockResolverFn: mock},
    );

    elementInstanceHistorySearchStore.setProcessInstanceKey(
      PROCESS_INSTANCE_KEY,
    );
    elementInstanceHistorySearchStore.setSearchText('Or');
    vi.advanceTimersByTime(100);
    elementInstanceHistorySearchStore.setSearchText('Ord');
    vi.advanceTimersByTime(100);
    elementInstanceHistorySearchStore.setSearchText('Order');
    expect(mock).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(350);

    await waitFor(() => {
      expect(elementInstanceHistorySearchStore.state.status).toBe('loaded');
    });
    expect(mock).toHaveBeenCalledTimes(1);
    expect(elementInstanceHistorySearchStore.state.items).toHaveLength(1);
  });

  it('clears items and stops polling when searchText is cleared', async () => {
    mockSearchElementInstances().withSuccess(
      createMockResponse([createMockElementInstance()], 1),
    );

    elementInstanceHistorySearchStore.setProcessInstanceKey(
      PROCESS_INSTANCE_KEY,
    );
    elementInstanceHistorySearchStore.setSearchText('Order');
    await vi.advanceTimersByTimeAsync(350);
    await waitFor(() =>
      expect(elementInstanceHistorySearchStore.state.status).toBe('loaded'),
    );

    elementInstanceHistorySearchStore.setSearchText('');
    expect(elementInstanceHistorySearchStore.state.items).toEqual([]);
    expect(elementInstanceHistorySearchStore.state.status).toBe('idle');
    expect(elementInstanceHistorySearchStore.hasActiveSearch).toBe(false);
  });

  it('switches to error-permissions on HTTP 403', async () => {
    mockSearchElementInstances().withServerError(403);

    elementInstanceHistorySearchStore.setProcessInstanceKey(
      PROCESS_INSTANCE_KEY,
    );
    elementInstanceHistorySearchStore.setSearchText('Order');
    await vi.advanceTimersByTimeAsync(350);

    await waitFor(() =>
      expect(elementInstanceHistorySearchStore.state.status).toBe(
        'error-permissions',
      ),
    );
  });

  it('switches to error on a 500 response', async () => {
    mockSearchElementInstances().withServerError(500);

    elementInstanceHistorySearchStore.setProcessInstanceKey(
      PROCESS_INSTANCE_KEY,
    );
    elementInstanceHistorySearchStore.setSearchText('Order');
    await vi.advanceTimersByTimeAsync(350);

    await waitFor(() =>
      expect(elementInstanceHistorySearchStore.state.status).toBe('error'),
    );
  });

  it('does nothing when processInstanceKey is not set', async () => {
    const mock = vi.fn();
    mockSearchElementInstances().withSuccess(createMockResponse([], 0), {
      mockResolverFn: mock,
    });

    elementInstanceHistorySearchStore.setSearchText('Order');
    await vi.advanceTimersByTimeAsync(400);

    expect(mock).not.toHaveBeenCalled();
    expect(elementInstanceHistorySearchStore.state.status).toBe('idle');
  });

  it('resets state when processInstanceKey changes', async () => {
    mockSearchElementInstances().withSuccess(
      createMockResponse([createMockElementInstance()], 1),
    );

    elementInstanceHistorySearchStore.setProcessInstanceKey(
      PROCESS_INSTANCE_KEY,
    );
    elementInstanceHistorySearchStore.setSearchText('Order');
    await vi.advanceTimersByTimeAsync(350);
    await waitFor(() =>
      expect(elementInstanceHistorySearchStore.state.status).toBe('loaded'),
    );

    elementInstanceHistorySearchStore.setProcessInstanceKey('9999999999');
    expect(elementInstanceHistorySearchStore.state.searchText).toBe('');
    expect(elementInstanceHistorySearchStore.state.items).toEqual([]);
    expect(elementInstanceHistorySearchStore.state.status).toBe('idle');
    expect(elementInstanceHistorySearchStore.state.processInstanceKey).toBe(
      '9999999999',
    );
  });
});
