/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {Decisions} from './';
import {LocationLog} from 'modules/utils/LocationLog';
import {notificationsStore} from 'modules/stores/notifications';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {mockFetchDecisionDefinitionXML} from 'modules/mocks/api/v2/decisionDefinitions/fetchDecisionDefinitionXML';
import {mockQueryBatchOperations} from 'modules/mocks/api/v2/batchOperations/queryBatchOperations';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';
import {mockEmptyDecisionInstancesSearchResult} from 'modules/mocks/mockDecisionInstanceSearch';
import {mockSearchDecisionDefinitions} from 'modules/mocks/api/v2/decisionDefinitions/searchDecisionDefinitions';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

function createWrapper(initialPath: string = Paths.decisions()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
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
    mockQueryBatchOperations().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchDecisionDefinitions().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);
    mockMe().withSuccess(createUser());

    render(<Decisions />, {wrapper: createWrapper()});

    expect(document.title).toBe('Operate: Decision Instances');
  });

  it('should redirect to initial decisions page if decision name does not exist', async () => {
    mockQueryBatchOperations().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchDecisionDefinitions().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchDecisionDefinitions().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    mockSearchDecisionInstances().withSuccess(
      mockEmptyDecisionInstancesSearchResult,
    );
    mockSearchDecisionInstances().withSuccess(
      mockEmptyDecisionInstancesSearchResult,
    );
    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);
    mockMe().withSuccess(createUser());

    const queryString =
      '?evaluated=true&failed=true&name=non-existing-decision&version=all';
    render(<Decisions />, {
      wrapper: createWrapper(`/decisions${queryString}`),
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    expect(screen.getByTestId('search').textContent).toBe(queryString);

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions/);
    await waitFor(() =>
      expect(screen.getByTestId('search').textContent).toBe(
        '?evaluated=true&failed=true',
      ),
    );

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'error',
      title: 'Decision could not be found',
    });
  });
});
