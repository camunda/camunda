/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  fireEvent,
  waitForElementToBeRemoved,
  waitFor,
} from '@testing-library/react';
import {Router} from 'react-router-dom';
import {createMemoryHistory} from 'history';
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

    fireEvent.change(screen.getByLabelText(/username/i), {
      target: {
        value: 'demo',
      },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: {
        value: 'demo',
      },
    });
    fireEvent.click(screen.getByRole('button', {name: /log in/i}));

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

    fireEvent.change(screen.getByLabelText(/username/i), {
      target: {
        value: 'demo',
      },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: {
        value: 'demo',
      },
    });
    fireEvent.click(screen.getByRole('button', {name: /log in/i}));

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('spinner'));

    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text('')))
    );

    fireEvent.click(screen.getByRole('button', {name: /log in/i}));

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('spinner'));
  });

  it('should redirect to the previous page', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text('')))
    );

    const INITIAL_ROUTE = '/instances';
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

    fireEvent.change(screen.getByLabelText(/username/i), {
      target: {
        value: 'demo',
      },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: {
        value: 'demo',
      },
    });
    fireEvent.click(screen.getByRole('button', {name: /log in/i}));

    await waitFor(() =>
      expect(mockHistory.location.pathname).toBe(INITIAL_ROUTE)
    );
  });

  it('should redirect to the previous page with gse url', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text('')))
    );

    const INITIAL_ROUTE = '/instances';
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

    fireEvent.change(screen.getByLabelText(/username/i), {
      target: {
        value: 'demo',
      },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: {
        value: 'demo',
      },
    });
    fireEvent.click(screen.getByRole('button', {name: /log in/i}));

    await waitFor(() =>
      expect(mockHistory.location.pathname).toBe(INITIAL_ROUTE)
    );
    expect(mockHistory.location.search).toBe('?gseUrl=https://www.testUrl.com');
  });

  it('should disable the login button when any field is empty', () => {
    render(<Login />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByRole('button', {name: /log in/i})).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/username/i), {
      target: {
        value: 'demo',
      },
    });

    expect(screen.getByRole('button', {name: /log in/i})).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/password/i), {
      target: {
        value: 'demo',
      },
    });

    expect(screen.getByRole('button', {name: /log in/i})).toBeEnabled();

    fireEvent.change(screen.getByLabelText(/username/i), {
      target: {
        value: '',
      },
    });

    expect(screen.getByRole('button', {name: /log in/i})).toBeDisabled();
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

    fireEvent.change(screen.getByLabelText(/username/i), {
      target: {
        value: 'wrong',
      },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: {
        value: 'credentials',
      },
    });
    fireEvent.click(screen.getByRole('button', {name: /log in/i}));

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

    fireEvent.change(screen.getByLabelText(/username/i), {
      target: {
        value: 'demo',
      },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: {
        value: 'demo',
      },
    });
    fireEvent.click(screen.getByRole('button', {name: /log in/i}));

    expect(await screen.findByText(GENERIC_ERROR)).toBeInTheDocument();
  });

  it('should handle request failures', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res) => res.networkError('Request failed'))
    );

    render(<Login />, {
      wrapper: createWrapper(),
    });

    fireEvent.change(screen.getByLabelText(/username/i), {
      target: {
        value: 'demo',
      },
    });
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: {
        value: 'demo',
      },
    });
    fireEvent.click(screen.getByRole('button', {name: /log in/i}));

    expect(await screen.findByText(GENERIC_ERROR)).toBeInTheDocument();
  });
});
