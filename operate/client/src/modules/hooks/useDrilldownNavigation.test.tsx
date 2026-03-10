/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, act} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {useDrillDownNavigation} from './useDrilldownNavigation';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {notificationsStore} from 'modules/stores/notifications';
import {Paths} from 'modules/Routes';
import {createProcessInstance, searchResult} from 'modules/testUtils';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual =
    await vi.importActual<typeof import('react-router-dom')>(
      'react-router-dom',
    );
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const PROCESS_INSTANCE_KEY = '2251799813685249';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {queries: {retry: false}},
  });

  return ({children}: {children: React.ReactNode}) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe('useDrillDownNavigation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should navigate directly to the called instance when there is exactly one', async () => {
    const calledInstance = createProcessInstance({
      processInstanceKey: 'called-200',
    });

    mockSearchProcessInstances().withSuccess(searchResult([calledInstance], 1));

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown();
    });

    expect(mockNavigate).toHaveBeenCalledWith(
      Paths.processInstance('called-200'),
    );
  });

  it('should not navigate when there are multiple called instances', async () => {
    const calledInstances = [
      createProcessInstance({processInstanceKey: 'called-200'}),
      createProcessInstance({processInstanceKey: 'called-201'}),
    ];

    mockSearchProcessInstances().withSuccess(searchResult(calledInstances, 2));

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown();
    });

    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('should not navigate when there are no called instances', async () => {
    mockSearchProcessInstances().withSuccess(searchResult([], 0));

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown();
    });

    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('should show error toast when API call fails', async () => {
    mockSearchProcessInstances().withServerError();

    const {result} = renderHook(
      () => useDrillDownNavigation(PROCESS_INSTANCE_KEY),
      {wrapper: createWrapper()},
    );

    await act(async () => {
      result.current.handleDrillDown();
    });

    expect(mockNavigate).not.toHaveBeenCalled();
    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Failed to resolve called instances',
      isDismissable: true,
    });
  });
});
