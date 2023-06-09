/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Decisions} from './';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {LocationLog} from 'modules/utils/LocationLog';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {useNotifications} from 'modules/notifications';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {mockFetchDecisionXML} from 'modules/mocks/api/decisions/fetchDecisionXML';
import {mockFetchGroupedDecisions} from 'modules/mocks/api/decisions/fetchGroupedDecisions';
import {mockFetchDecisionInstances} from 'modules/mocks/api/decisionInstances/fetchDecisionInstances';
import {useEffect} from 'react';
import {mockFetchBatchOperations} from 'modules/mocks/api/fetchBatchOperations';

const handleRefetchSpy = jest.spyOn(groupedDecisionsStore, 'handleRefetch');

jest.mock('modules/notifications', () => {
  const mockUseNotifications = {
    displayNotification: jest.fn(),
  };

  return {
    useNotifications: () => {
      return mockUseNotifications;
    },
  };
});

function createWrapper(initialPath: string = '/decisions') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        decisionInstancesStore.reset();
        groupedDecisionsStore.reset();
        decisionXmlStore.reset();
      };
    }, []);
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          {children}
          <LocationLog />
        </MemoryRouter>
      </ThemeProvider>
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
    mockFetchDecisionXML().withSuccess(mockDmnXml);

    render(<Decisions />, {wrapper: createWrapper()});

    expect(document.title).toBe('Operate: Decision Instances');

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('data-table-skeleton')
    );
    await waitFor(() =>
      expect(groupedDecisionsStore.state.status).toBe('fetched')
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
    mockFetchDecisionXML().withSuccess(mockDmnXml);

    render(<Decisions />, {
      wrapper: createWrapper(`/decisions${queryString}`),
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    expect(screen.getByTestId('search').textContent).toBe(queryString);

    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(1));

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
    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(groupedDecisionsStore.decisions.length).toBe(3);
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions/);
      expect(screen.getByTestId('search').textContent).toBe(
        '?evaluated=true&failed=true'
      );
    });

    expect(useNotifications().displayNotification).toHaveBeenCalledWith(
      'error',
      {
        headline: 'Decision could not be found',
      }
    );

    jest.clearAllTimers();
    jest.useRealTimers();

    locationSpy.mockRestore();
  });
});
