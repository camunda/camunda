/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {Link, MemoryRouter, To} from 'react-router-dom';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Login} from './index';
import {LOGIN_ERROR, GENERIC_ERROR} from './constants';
import {LocationLog} from 'modules/utils/LocationLog';
import {authenticationStore} from 'modules/stores/authentication';

function createWrapper(
  initialPath: string = '/',
  referrer: To = {pathname: '/processes'}
) {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          {children}
          <Link
            to="/login"
            state={{
              referrer,
            }}
          >
            emulate auth check
          </Link>
          <LocationLog />
        </MemoryRouter>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('<Login />', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should login', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text('')))
    );

    render(<Login />, {
      wrapper: createWrapper('/login'),
    });

    userEvent.type(screen.getByLabelText(/username/i), 'demo');
    userEvent.type(screen.getByLabelText(/password/i), 'demo');
    userEvent.click(screen.getByRole('button', {name: /log in/i}));

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent('/')
    );
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

    render(<Login />, {
      wrapper: createWrapper('/login'),
    });

    userEvent.click(screen.getByText(/emulate auth check/i));

    userEvent.type(screen.getByLabelText(/username/i), 'demo');
    userEvent.type(screen.getByLabelText(/password/i), 'demo');
    userEvent.click(screen.getByRole('button', {name: /log in/i}));

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent('/processes')
    );
  });

  it('should redirect to the previous page with gse url', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text('')))
    );

    render(<Login />, {
      wrapper: createWrapper('/login', {
        pathname: '/processes',
        search: '?gseUrl=https://www.testUrl.com',
      }),
    });

    userEvent.click(screen.getByText(/emulate auth check/i));

    userEvent.type(screen.getByLabelText(/username/i), 'demo');
    userEvent.type(screen.getByLabelText(/password/i), 'demo');
    userEvent.click(screen.getByRole('button', {name: /log in/i}));

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent('/processes')
    );
    expect(screen.getByTestId('search')).toHaveTextContent(
      '?gseUrl=https://www.testUrl.com'
    );
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
