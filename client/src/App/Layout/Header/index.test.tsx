/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Router, Route} from 'react-router-dom';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {Header} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {createMemoryHistory} from 'history';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {instancesStore} from 'modules/stores/instances';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {storeStateLocally, clearStateLocally} from 'modules/utils/localStorage';
import {authenticationStore} from 'modules/stores/authentication';

function createWrapper(history = createMemoryHistory()) {
  const Wrapper: React.FC = ({children}) => (
    <ThemeProvider>
      <Router history={history}>
        <Route>{children}</Route>
      </Router>
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
            displayName: 'firstname lastname',
            canLogout: false,
            permissions: ['read', 'write'],
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

    authenticationStore.authenticate();
  });

  afterEach(() => {
    currentInstanceStore.reset();
    authenticationStore.reset();
    instancesStore.reset();
    clearStateLocally();
  });

  it('should render all header links', async () => {
    render(<Header />, {
      wrapper: createWrapper(),
    });

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    expect(screen.getByText('Operate')).toBeInTheDocument();
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Instances')).toBeInTheDocument();
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

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    userEvent.click(await screen.findByText('Operate'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe('');

    userEvent.click(await screen.findByText('Instances'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?active=true&incidents=true&completed=true'
    );

    userEvent.click(await screen.findByText('Dashboard'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe('');
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

    expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

    userEvent.click(await screen.findByText('Operate'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );

    userEvent.click(await screen.findByText('Instances'));
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com&active=true&incidents=true&completed=true'
    );

    userEvent.click(await screen.findByText('Dashboard'));
    expect(MOCK_HISTORY.location.pathname).toBe('/');
    expect(MOCK_HISTORY.location.search).toBe(
      '?gseUrl=https%3A%2F%2Fwww.testUrl.com'
    );
  });

  describe('license note', () => {
    afterEach(() => {
      window.clientConfig = undefined;
    });

    it('should show license note in CCSM free/trial environment', async () => {
      window.clientConfig = {
        isEnterprise: false,
        organizationId: null,
      };

      render(<Header />, {
        wrapper: createWrapper(),
      });

      expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

      expect(screen.getByText('Non-Production License')).toBeInTheDocument();
    });

    it('should not show license note in SaaS environment', async () => {
      window.clientConfig = {
        isEnterprise: false,
        organizationId: '000000000-0000-0000-0000-000000000000',
      };

      render(<Header />, {
        wrapper: createWrapper(),
      });

      expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

      expect(
        screen.queryByText('Non-Production License')
      ).not.toBeInTheDocument();
    });

    it('should not show license note in CCSM enterprise environment', async () => {
      window.clientConfig = {
        isEnterprise: true,
        organizationId: null,
      };

      render(<Header />, {
        wrapper: createWrapper(),
      });

      expect(await screen.findByText('firstname lastname')).toBeInTheDocument();

      expect(
        screen.queryByText('Non-Production License')
      ).not.toBeInTheDocument();
    });
  });
});
