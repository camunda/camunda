/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {Router} from 'react-router-dom';
import {createMemoryHistory} from 'history';
import {rest} from 'msw';

import {Login} from './index';
import {login} from 'modules/stores/login';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {mockServer} from 'modules/mockServer';

const getFullYearMock = jest.spyOn(Date.prototype, 'getFullYear');
function createWrapper(
  history = createMemoryHistory({initialEntries: ['/login']}),
) {
  const Wrapper: React.FC = ({children}) => (
    <Router history={history}>
      <MockThemeProvider>{children}</MockThemeProvider>
    </Router>
  );
  return Wrapper;
}

describe('<Login />', () => {
  afterEach(() => {
    getFullYearMock.mockClear();
    login.reset();
  });

  afterAll(() => {
    getFullYearMock.mockRestore();
  });

  it('should redirect to the initial page on success', async () => {
    const historyMock = createMemoryHistory({initialEntries: ['/login']});
    login.disableSession();
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text(''))),
    );

    render(<Login />, {
      wrapper: createWrapper(historyMock),
    });

    userEvent.type(screen.getByPlaceholderText('Username'), 'demo');
    userEvent.type(screen.getByPlaceholderText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() => expect(historyMock.location.pathname).toBe('/'));
  });

  it('should redirect to the referrer page', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text(''))),
    );
    login.disableSession();
    const referrer = createMemoryHistory({
      initialEntries: [
        {
          pathname: '/1',
          search: '?filter=unclaimed',
        },
      ],
    }).location;
    const historyMock = createMemoryHistory({
      initialEntries: [
        {
          pathname: '/login',
          state: {
            referrer,
          },
        },
      ],
    });
    render(<Login />, {
      wrapper: createWrapper(historyMock),
    });

    userEvent.type(screen.getByPlaceholderText('Username'), 'demo');
    userEvent.type(screen.getByPlaceholderText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() => expect(historyMock.location.pathname).toBe('/1'));
    expect(historyMock.location.search).toBe('?filter=unclaimed');
  });

  it('should show an error for wrong credentials', async () => {
    login.disableSession();
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) =>
        res.once(ctx.status(401), ctx.text('')),
      ),
    );
    render(<Login />, {
      wrapper: createWrapper(),
    });

    userEvent.type(screen.getByPlaceholderText('Username'), 'demo');
    userEvent.type(screen.getByPlaceholderText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      await screen.findByText('Username and Password do not match'),
    ).toBeInTheDocument();
  });

  it('should show a generic error message', async () => {
    login.disableSession();
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) =>
        res.once(ctx.status(404), ctx.text('')),
      ),
    );
    render(<Login />, {
      wrapper: createWrapper(),
    });

    userEvent.type(screen.getByPlaceholderText('Username'), 'demo');
    userEvent.type(screen.getByPlaceholderText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      await screen.findByText('Credentials could not be verified'),
    ).toBeInTheDocument();

    mockServer.use(
      rest.post('/api/login', (_, res) => res.networkError('A network error')),
    );

    userEvent.type(screen.getByPlaceholderText('Username'), 'demo');
    userEvent.type(screen.getByPlaceholderText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      await screen.findByText('Credentials could not be verified'),
    ).toBeInTheDocument();
  });

  it('should show a loading overlay while the login form is submitting', async () => {
    login.disableSession();
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text(''))),
    );

    render(<Login />, {
      wrapper: createWrapper(),
    });

    userEvent.type(screen.getByPlaceholderText('Username'), 'demo');
    userEvent.type(screen.getByPlaceholderText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      await screen.findByTestId('login-loading-overlay'),
    ).toBeInTheDocument();
  });

  it('should have the correct copyright notice', () => {
    const mockYear = 1984;
    getFullYearMock.mockReturnValue(mockYear);
    login.disableSession();
    render(<Login />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.getByText(
        `© Camunda Services GmbH ${mockYear}. All rights reserved.`,
      ),
    ).toBeInTheDocument();
  });

  it('should not allow the form to be submitted with empty fields', async () => {
    login.disableSession();
    const historyMock = createMemoryHistory({initialEntries: ['/login']});

    render(<Login />, {
      wrapper: createWrapper(historyMock),
    });

    expect(screen.getByRole('button', {name: 'Login'})).toBeDisabled();

    userEvent.type(screen.getByPlaceholderText('Username'), 'demo');

    expect(screen.getByRole('button', {name: 'Login'})).toBeDisabled();

    userEvent.type(screen.getByPlaceholderText('Password'), 'demo');

    expect(screen.getByRole('button', {name: 'Login'})).toBeEnabled();

    userEvent.clear(screen.getByPlaceholderText('Username'));

    expect(screen.getByRole('button', {name: 'Login'})).toBeDisabled();

    userEvent.clear(screen.getByPlaceholderText('Password'));

    expect(screen.getByRole('button', {name: 'Login'})).toBeDisabled();
  });
});
