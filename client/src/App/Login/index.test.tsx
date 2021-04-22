/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {Router} from 'react-router-dom';
import {createMemoryHistory, createLocation} from 'history';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

import {Login} from './index';
import {LOGIN_ERROR, GENERIC_ERROR} from './constants';

function createWrapper(history = createMemoryHistory()) {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <Router history={history}>{children}</Router>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('<Login />', () => {
  it('should login', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text('')))
    );

    const mockHistory = createMemoryHistory({initialEntries: ['/login']});
    render(<Login />, {
      wrapper: createWrapper(mockHistory),
    });

    userEvent.type(screen.getByLabelText(/username/i), 'demo');
    userEvent.type(screen.getByLabelText(/password/i), 'demo');
    userEvent.click(screen.getByRole('button', {name: /log in/i}));

    await waitFor(() => expect(mockHistory.location.pathname).toBe('/'));
  });

  it('should show a loading spinner', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.text(''))
      )
    );

    render(<Login />, {
      wrapper: createWrapper(),
    });

    userEvent.type(screen.getByLabelText(/username/i), 'demo');
    userEvent.type(screen.getByLabelText(/password/i), 'demo');
    userEvent.click(screen.getByRole('button', {name: /log in/i}));

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('spinner'));

    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text('')))
    );

    userEvent.click(screen.getByRole('button', {name: /log in/i}));

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('spinner'));
  });

  it('should redirect to the previous page', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text('')))
    );

    const INITIAL_ROUTE = createLocation({
      pathname: '/instances',
    });
    const mockHistory = createMemoryHistory();
    mockHistory.push({
      pathname: '/login',
      state: {
        referrer: INITIAL_ROUTE,
      },
    });

    render(<Login />, {
      wrapper: createWrapper(mockHistory),
    });

    userEvent.type(screen.getByLabelText(/username/i), 'demo');
    userEvent.type(screen.getByLabelText(/password/i), 'demo');
    userEvent.click(screen.getByRole('button', {name: /log in/i}));

    await waitFor(() =>
      expect(mockHistory.location.pathname).toBe(INITIAL_ROUTE.pathname)
    );
  });

  it('should redirect to the previous page with gse url', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text('')))
    );

    const INITIAL_ROUTE = createLocation({
      pathname: '/instances',
      search: '?gseUrl=https://www.testUrl.com',
    });
    const mockHistory = createMemoryHistory();
    mockHistory.push({
      pathname: '/login',
      state: {
        referrer: INITIAL_ROUTE,
      },
      search: '?gseUrl=https://www.testUrl.com',
    });

    render(<Login />, {
      wrapper: createWrapper(mockHistory),
    });

    userEvent.type(screen.getByLabelText(/username/i), 'demo');
    userEvent.type(screen.getByLabelText(/password/i), 'demo');
    userEvent.click(screen.getByRole('button', {name: /log in/i}));

    await waitFor(() =>
      expect(mockHistory.location.pathname).toBe(INITIAL_ROUTE.pathname)
    );
    expect(mockHistory.location.search).toBe(INITIAL_ROUTE.search);
  });

  it('should disable the login button when any field is empty', () => {
    const originalConsoleError = global.console.error;
    global.console.error = jest.fn();

    render(<Login />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByRole('button', {name: /log in/i})).toBeDisabled();

    userEvent.type(screen.getByLabelText(/username/i), 'demo');

    expect(screen.getByRole('button', {name: /log in/i})).toBeDisabled();

    userEvent.type(screen.getByLabelText(/password/i), 'demo');

    expect(screen.getByRole('button', {name: /log in/i})).toBeEnabled();

    userEvent.clear(screen.getByLabelText(/password/i));
    userEvent.type(screen.getByLabelText(/username/i), '');

    expect(screen.getByRole('button', {name: /log in/i})).toBeDisabled();

    global.console.error = originalConsoleError;
  });

  it('should handle wrong credentials', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) =>
        res.once(ctx.status(401), ctx.text(''))
      )
    );

    render(<Login />, {
      wrapper: createWrapper(),
    });

    userEvent.type(screen.getByLabelText(/username/i), 'wrong');
    userEvent.type(screen.getByLabelText(/password/i), 'credentials');
    userEvent.click(screen.getByRole('button', {name: /log in/i}));

    expect(await screen.findByText(LOGIN_ERROR)).toBeInTheDocument();
  });

  it('should handle generic errors', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.text(''))
      )
    );

    render(<Login />, {
      wrapper: createWrapper(),
    });

    userEvent.type(screen.getByLabelText(/username/i), 'demo');
    userEvent.type(screen.getByLabelText(/password/i), 'demo');
    userEvent.click(screen.getByRole('button', {name: /log in/i}));

    expect(await screen.findByText(GENERIC_ERROR)).toBeInTheDocument();
  });

  it('should handle request failures', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res) => res.networkError('Request failed'))
    );

    render(<Login />, {
      wrapper: createWrapper(),
    });

    userEvent.type(screen.getByLabelText(/username/i), 'demo');
    userEvent.type(screen.getByLabelText(/password/i), 'demo');
    userEvent.click(screen.getByRole('button', {name: /log in/i}));

    expect(await screen.findByText(GENERIC_ERROR)).toBeInTheDocument();
  });
});
