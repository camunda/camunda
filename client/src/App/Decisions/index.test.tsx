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
} from '@testing-library/react';
import {rest} from 'msw';
import {MemoryRouter} from 'react-router-dom';
import {mockServer} from 'modules/mock-server/node';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Decisions} from './';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {LocationLog} from 'modules/utils/LocationLog';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {useNotifications} from 'modules/notifications';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';

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
  afterEach(() => {
    decisionInstancesStore.reset();
    groupedDecisionsStore.reset();
    decisionXmlStore.reset();
  });

  it('should show page title', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json({decisionInstances: [], totalCount: 0}))
      ),
      rest.get('/api/decisions/grouped', (_, res, ctx) => res(ctx.json([]))),
      rest.get('/api/decisions/:decisionDefinitionId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockDmnXml))
      )
    );

    render(<Decisions />, {wrapper: createWrapper()});

    expect(document.title).toBe('Operate: Decision Instances');

    await waitForElementToBeRemoved(() => screen.getByTestId('table-skeleton'));
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

    mockServer.use(
      rest.get('/api/decisions/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedDecisions))
      ),
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json({decisionInstances: [], totalCount: 0}))
      ),
      rest.get('/api/decisions/:decisionDefinitionId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockDmnXml))
      )
    );

    render(<Decisions />, {
      wrapper: createWrapper(`/decisions${queryString}`),
    });

    expect(screen.getByTestId('table-skeleton')).toBeInTheDocument();

    expect(screen.getByTestId('search').textContent).toBe(queryString);

    mockServer.use(
      rest.get('/api/decisions/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedDecisions))
      )
    );
    jest.runOnlyPendingTimers();

    mockServer.use(
      rest.get('/api/decisions/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedDecisions))
      )
    );
    jest.runOnlyPendingTimers();

    mockServer.use(
      rest.get('/api/decisions/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedDecisions))
      ),
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json({decisionInstances: [], totalCount: 0}))
      )
    );
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
