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
import {mockQueryBatchOperations} from 'modules/mocks/api/v2/batchOperations/queryBatchOperations';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';

const handleRefetchSpy = vi.spyOn(groupedDecisionsStore, 'handleRefetch');

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
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
    mockQueryBatchOperations().withSuccess({items: [], page: {totalItems: 0}});
    mockFetchGroupedDecisions().withSuccess([]);
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);
    mockMe().withSuccess(createUser());

    render(<Decisions />, {wrapper: createWrapper()});

    expect(document.title).toBe('Operate: Decision Instances');

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('data-table-skeleton'),
    );
    await waitFor(() =>
      expect(groupedDecisionsStore.state.status).toBe('fetched'),
    );
  });

  it.skip('should poll 3 times for grouped decisions and redirect to initial decisions page if decision name does not exist', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const queryString =
      '?evaluated=true&failed=true&name=non-existing-decision&version=all';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    mockQueryBatchOperations().withSuccess({items: [], page: {totalItems: 0}});
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

    vi.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(2));

    mockFetchGroupedDecisions().withSuccess(groupedDecisions);

    vi.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(3));

    mockFetchGroupedDecisions().withSuccess(groupedDecisions);

    mockFetchDecisionInstances().withSuccess({
      decisionInstances: [],
      totalCount: 0,
    });

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    vi.runOnlyPendingTimers();

    await waitFor(() => {
      expect(groupedDecisionsStore.decisions.length).toBe(4);
    });
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions/);
    expect(screen.getByTestId('search').textContent).toBe(
      '?evaluated=true&failed=true',
    );

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'error',
      title: 'Decision could not be found',
    });

    vi.clearAllTimers();
    vi.useRealTimers();
  });
});
