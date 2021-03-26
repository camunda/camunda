/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Router} from 'react-router-dom';

import {currentInstanceStore} from 'modules/stores/currentInstance';
import Header from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {createMemoryHistory} from 'history';
import {render, within, fireEvent, screen} from '@testing-library/react';
import {instancesStore} from 'modules/stores/instances';
import {statisticsStore} from 'modules/stores/statistics';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';

// Header component fetches user information in the background, which is an async action and might complete after the tests finished.
// Tests also depend on the statistics fetch to be completed, in order to test the results that are rendered in the screen.
// So we have to use this function in all tests, in order to verify all the async actions are completed, when we want them to be completed.
const waitForComponentToLoad = async () => {
  expect(await screen.findByText('firstname lastname')).toBeInTheDocument();
  expect(await screen.findByTitle('View 731 Incidents')).toBeInTheDocument();
};

function createWrapper(history = createMemoryHistory()) {
  const Wrapper: React.FC = ({children}) => (
    <ThemeProvider>
      <Router history={history}>{children}</Router>
    </ThemeProvider>
  );
  return Wrapper;
}

describe('Header', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/authentications/user', (_, res, ctx) =>
        res.once(
          ctx.json({
            firstname: 'firstname',
            lastname: 'lastname',
          })
        )
      ),
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            running: 821,
            active: 90,
            withIncidents: 731,
          })
        )
      ),
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'first_instance_id',
            state: 'ACTIVE',
          })
        )
      )
    );
  });

  afterEach(() => {
    currentInstanceStore.reset();
    instancesStore.reset();
    statisticsStore.reset();
    clearStateLocally();
  });

  it('should render all header links', async () => {
    render(<Header />, {
      wrapper: createWrapper(
        createMemoryHistory({
          initialEntries: ['/'],
        })
      ),
    });

    await waitForComponentToLoad();

    expect(screen.getByText('Camunda Operate')).toBeInTheDocument();
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Running Instances')).toBeInTheDocument();
    expect(screen.getByText('Filters')).toBeInTheDocument();
    expect(screen.getByText('Incidents')).toBeInTheDocument();
    expect(screen.queryByText('Instance')).toBeNull();
  });

  it('should render incident, filter and instances counts correctly', async () => {
    instancesStore.setInstances({
      filteredInstancesCount: 200,
      processInstances: [],
    });

    render(<Header />, {
      wrapper: createWrapper(
        createMemoryHistory({
          initialEntries: ['/'],
        })
      ),
    });

    expect(
      within(screen.getByTestId('header-link-incidents')).getByTestId('badge')
    ).toBeEmptyDOMElement();
    expect(
      within(screen.getByTestId('header-link-filters')).getByTestId('badge')
    ).toBeEmptyDOMElement();
    expect(
      within(screen.getByTestId('header-link-instances')).getByTestId('badge')
    ).toBeEmptyDOMElement();

    await waitForComponentToLoad();

    expect(
      within(screen.getByTestId('header-link-incidents')).getByTestId('badge')
    ).toHaveTextContent('731');
    expect(
      within(screen.getByTestId('header-link-filters')).getByTestId('badge')
    ).toHaveTextContent('200');
    expect(
      within(screen.getByTestId('header-link-instances')).getByTestId('badge')
    ).toHaveTextContent('821');
  });

  it('should render user element', async () => {
    render(<Header />, {
      wrapper: createWrapper(
        createMemoryHistory({
          initialEntries: ['/'],
        })
      ),
    });

    await waitForComponentToLoad();
    expect(screen.getByText('firstname lastname')).toBeInTheDocument();
  });

  it('should highlight links correctly on dashboard page', async () => {
    render(<Header />, {
      wrapper: createWrapper(
        createMemoryHistory({
          initialEntries: ['/'],
        })
      ),
    });
    await waitForComponentToLoad();

    expect(screen.getByText('Dashboard')).not.toHaveStyleRule('opacity', '0.5');
    expect(screen.getByText('Running Instances')).toHaveStyleRule(
      'opacity',
      '0.5'
    );
    expect(screen.getByText('Filters')).toHaveStyleRule('opacity', '0.5');
    expect(screen.getByText('Incidents')).toHaveStyleRule('opacity', '0.5');
  });

  it('should highlight links correctly on instances page', async () => {
    render(<Header />, {
      wrapper: createWrapper(
        createMemoryHistory({
          initialEntries: ['/instances?active=true&incidents=true'],
        })
      ),
    });
    await waitForComponentToLoad();

    expect(screen.getByText('Dashboard')).toHaveStyleRule('opacity', '0.5');
    expect(screen.getByText('Running Instances')).not.toHaveStyleRule(
      'opacity',
      '0.5'
    );
    expect(screen.getByText('Filters')).not.toHaveStyleRule('opacity', '0.5');
    expect(screen.getByText('Incidents')).toHaveStyleRule('opacity', '0.5');
  });

  it('should render instance details skeleton on instance view', async () => {
    render(<Header />, {
      wrapper: createWrapper(
        createMemoryHistory({
          initialEntries: ['/instances/1'],
        })
      ),
    });
    await waitForComponentToLoad();

    expect(screen.queryByTestId(/state-icon/)).not.toBeInTheDocument();
    expect(screen.getByTestId('instance-skeleton-circle')).toBeInTheDocument();
    expect(screen.getByTestId('instance-skeleton-block')).toBeInTheDocument();
  });

  it('should render instance details on instance view', async () => {
    const MOCK_INSTANCE_ID = 'first_instance_id';

    render(<Header />, {
      wrapper: createWrapper(
        createMemoryHistory({
          initialEntries: [`/instances/${MOCK_INSTANCE_ID}`],
        })
      ),
    });
    await waitForComponentToLoad();

    currentInstanceStore.init(MOCK_INSTANCE_ID);

    expect(
      await screen.findByText(`Instance ${MOCK_INSTANCE_ID}`)
    ).toBeInTheDocument();
    expect(screen.getByTestId(/state-icon/)).toBeInTheDocument();
    expect(
      screen.queryByTestId('instance-skeleton-circle')
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('instance-skeleton-block')
    ).not.toBeInTheDocument();
  });

  it('should render instance details on refresh', async () => {
    const MOCK_FIRST_INSTANCE_ID = 'first_instance_id';
    const MOCK_SECOND_INSTANCE_ID = 'second_instance_id';

    render(<Header />, {
      wrapper: createWrapper(
        createMemoryHistory({
          initialEntries: [`/instances/${MOCK_FIRST_INSTANCE_ID}`],
        })
      ),
    });
    await waitForComponentToLoad();

    jest.useFakeTimers();
    currentInstanceStore.init(MOCK_FIRST_INSTANCE_ID);
    expect(
      await screen.findByText(`Instance ${MOCK_FIRST_INSTANCE_ID}`)
    ).toBeInTheDocument();

    mockServer.use(
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            running: 821,
            active: 90,
            withIncidents: 732,
          })
        )
      ),
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: 'second_instance_id',
            state: 'ACTIVE',
          })
        )
      )
    );

    jest.runOnlyPendingTimers();

    expect(await screen.findByTitle('View 732 Incidents')).toBeInTheDocument();
    expect(
      await screen.findByText(`Instance ${MOCK_SECOND_INSTANCE_ID}`)
    ).toBeInTheDocument();
    expect(screen.queryByText(`Instance ${MOCK_FIRST_INSTANCE_ID}`)).toBeNull();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should go to the correct pages when clicking on header links', async () => {
    storeStateLocally({
      filters: {
        active: true,
        incidents: true,
        completed: true,
      },
    });
    const MOCK_HISTORY = createMemoryHistory();
    render(<Header />, {
      wrapper: createWrapper(MOCK_HISTORY),
    });

    await waitForComponentToLoad();

    fireEvent.click(await screen.findByText('Camunda Operate'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe('');

    fireEvent.click(await screen.findByText('Running Instances'));
    expect(MOCK_HISTORY.location.search).toBe('?active=true&incidents=true');

    fireEvent.click(await screen.findByText('Dashboard'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe('');

    fireEvent.click(await screen.findByText('Filters'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?active=true&incidents=true&completed=true'
    );

    fireEvent.click(await screen.findByText('Incidents'));
    expect(MOCK_HISTORY.location.search).toBe('?incidents=true');
  });

  it('should preserve persistent params', async () => {
    storeStateLocally({
      filters: {
        active: true,
        incidents: true,
        completed: true,
      },
    });
    const MOCK_HISTORY = createMemoryHistory({
      initialEntries: ['/?gseUrl=https://www.testUrl.com'],
    });
    render(<Header />, {
      wrapper: createWrapper(MOCK_HISTORY),
    });

    await waitForComponentToLoad();

    fireEvent.click(await screen.findByText('Camunda Operate'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );

    fireEvent.click(await screen.findByText('Running Instances'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&active=true&incidents=true'
    );

    fireEvent.click(await screen.findByText('Dashboard'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );

    fireEvent.click(await screen.findByText('Filters'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&active=true&incidents=true&completed=true'
    );

    fireEvent.click(await screen.findByText('Incidents'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&incidents=true'
    );
  });
});
