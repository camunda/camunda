/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen, fireEvent, waitFor} from '@testing-library/react';

import {Header} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {Router} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {login} from 'modules/stores/login';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {rest, graphql} from 'msw';
import {mockServer} from 'modules/mockServer';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';

function createWrapper(history = createMemoryHistory()) {
  const Wrapper: React.FC = ({children}) => (
    <ApolloProvider client={client}>
      <Router history={history}>
        <MockThemeProvider>{children}</MockThemeProvider>
      </Router>
    </ApolloProvider>
  );
  return Wrapper;
}

describe('<Header />', () => {
  afterEach(() => {
    login.reset();
  });

  beforeEach(() => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
    );
  });

  it('should render header', async () => {
    render(<Header />, {
      wrapper: createWrapper(),
    });
    expect(screen.getByText('Tasklist')).toBeInTheDocument();
    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.getByTestId('logo')).toBeInTheDocument();
    expect(screen.getByTestId('dropdown-icon')).toBeInTheDocument();
  });

  it('should navigate to home page when brand label is clicked', async () => {
    const historyMock = createMemoryHistory();

    render(<Header />, {
      wrapper: createWrapper(historyMock),
    });
    await screen.findByText('Demo User');

    fireEvent.click(screen.getByText('Tasklist'));

    expect(historyMock.location.pathname).toBe('/');
    expect(historyMock.location.search).toBe('');
  });

  it('should navigate to home page when brand label is clicked (with gse url)', async () => {
    const historyMock = createMemoryHistory({
      initialEntries: ['/?gseUrl=https://www.testUrl.com'],
    });

    render(<Header />, {
      wrapper: createWrapper(historyMock),
    });
    await screen.findByText('Demo User');

    fireEvent.click(screen.getByText('Tasklist'));

    expect(historyMock.location.pathname).toBe('/');
    expect(historyMock.location.search).toBe(
      'gseUrl=https%3A%2F%2Fwww.testUrl.com',
    );
  });

  it('should handle logout', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res) => res.once()),
      rest.post('/api/logout', (_, res) => res.once()),
    );
    await login.handleLogin('demo', 'demo');

    render(<Header />, {
      wrapper: createWrapper(),
    });

    expect(login.status).toBe('logged-in');

    fireEvent.click(await screen.findByText('Demo User'));
    fireEvent.click(screen.getByText('Logout'));

    await waitFor(() => expect(login.status).toBe('logged-out'));
    expect(screen.queryByText('logout')).not.toBeInTheDocument();
  });

  describe('license note', () => {
    beforeEach(() => {
      Object.defineProperties(window.clientConfig, {
        isEnterprise: {
          configurable: true,
          writable: true,
          value: null,
        },
        organizationId: {
          configurable: true,
          writable: true,
          value: null,
        },
      });
    });
    afterEach(() => {
      delete window.clientConfig!.isEnterprise;
      delete window.clientConfig!.organizationId;
    });

    it('should show license note in CCSM free/trial environment', () => {
      window.clientConfig!.isEnterprise = false;

      render(<Header />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByText('Non-Production License')).toBeInTheDocument();
    });

    it('should not show license note in SaaS environment', () => {
      window.clientConfig!.isEnterprise = false;
      window.clientConfig!.organizationId =
        '000000000-0000-0000-0000-000000000000';

      render(<Header />, {
        wrapper: createWrapper(),
      });

      expect(
        screen.queryByText('Non-Production License'),
      ).not.toBeInTheDocument();
    });

    it('should not show license note in CCSM enterprise environment', () => {
      window.clientConfig!.isEnterprise = true;

      render(<Header />, {
        wrapper: createWrapper(),
      });

      expect(
        screen.queryByText('Non-Production License'),
      ).not.toBeInTheDocument();
    });
  });
});
