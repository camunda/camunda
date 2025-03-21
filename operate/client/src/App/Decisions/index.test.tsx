/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {Decisions} from './';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {LocationLog} from 'modules/utils/LocationLog';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {notificationsStore} from 'modules/stores/notifications';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {mockFetchDecisionDefinitionXML} from 'modules/mocks/api/v2/decisionDefinitions/fetchDecisionDefinitionXML';
import {mockFetchGroupedDecisions} from 'modules/mocks/api/decisions/fetchGroupedDecisions';
import {mockFetchDecisionInstances} from 'modules/mocks/api/decisionInstances/fetchDecisionInstances';
import {useEffect} from 'react';
import {mockFetchBatchOperations} from 'modules/mocks/api/fetchBatchOperations';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const handleRefetchSpy = jest.spyOn(groupedDecisionsStore, 'handleRefetch');

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

function createWrapper(initialPath: string = Paths.decisions()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        decisionInstancesStore.reset();
        groupedDecisionsStore.reset();
      };
    }, []);
    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[initialPath]}>
          {children}
          <LocationLog />
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('<Decisions />', () => {
  it('should show page title', async () => {
    mockFetchDecisionInstances().withSuccess({
      decisionInstances: [],
      totalCount: 0,
    });
    mockFetchBatchOperations().withSuccess([]);
    mockFetchGroupedDecisions().withSuccess([]);
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);

    render(<Decisions />, {wrapper: createWrapper()});

    expect(document.title).toBe('Operate: Decision Instances');

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('data-table-skeleton'),
    );
    await waitFor(() =>
      expect(groupedDecisionsStore.state.status).toBe('fetched'),
    );
  });

  it('should poll 3 times for grouped decisions and redirect to initial decisions page if decision name does not exist', async () => {
    jest.useFakeTimers();

    const queryString =
      '?evaluated=true&failed=true&name=non-existing-decision&version=all';

    const originalWindow = {...window};

    const locationSpy = jest.spyOn(window, 'location', 'get');

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    mockFetchBatchOperations().withSuccess([]);
    mockFetchGroupedDecisions().withSuccess(groupedDecisions);
    mockFetchDecisionInstances().withSuccess({
      decisionInstances: [],
      totalCount: 0,
    });
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);

    render(<Decisions />, {
      wrapper: createWrapper(`/decisions${queryString}`),
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    expect(screen.getByTestId('search').textContent).toBe(queryString);

    mockFetchGroupedDecisions().withSuccess(groupedDecisions);

    await waitFor(() =>
      expect(groupedDecisionsStore.state.status).toBe('fetching'),
    );
    expect(handleRefetchSpy).toHaveBeenCalledTimes(1);

    mockFetchGroupedDecisions().withSuccess(groupedDecisions);

    jest.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(2));

    mockFetchGroupedDecisions().withSuccess(groupedDecisions);

    jest.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(3));

    mockFetchGroupedDecisions().withSuccess(groupedDecisions);

    mockFetchDecisionInstances().withSuccess({
      decisionInstances: [],
      totalCount: 0,
    });

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(groupedDecisionsStore.decisions.length).toBe(4);
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions/);
      expect(screen.getByTestId('search').textContent).toBe(
        '?evaluated=true&failed=true',
      );
    });

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'error',
      title: 'Decision could not be found',
    });

    jest.clearAllTimers();
    jest.useRealTimers();

    locationSpy.mockRestore();
  });
});
