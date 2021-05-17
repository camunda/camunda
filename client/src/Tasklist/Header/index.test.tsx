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
import {MockedApolloProvider} from 'modules/mock-schema/MockedApolloProvider';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

function createWrapper(history = createMemoryHistory()) {
  const Wrapper: React.FC = ({children}) => (
    <MockedApolloProvider mocks={[mockGetCurrentUser]}>
      <Router history={history}>
        <MockThemeProvider>{children}</MockThemeProvider>
      </Router>
    </MockedApolloProvider>
  );
  return Wrapper;
}

describe('<Header />', () => {
  afterEach(() => {
    login.reset();
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
});
