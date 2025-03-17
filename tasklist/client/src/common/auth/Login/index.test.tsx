/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  fireEvent,
  render,
  screen,
  waitFor,
} from 'common/testing/testing-library';
import {Link, MemoryRouter} from 'react-router-dom';
import {http, HttpResponse} from 'msw';
import {Component} from './index';
import {authenticationStore} from 'common/auth/authentication';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {LocationLog} from 'common/testing/LocationLog';

function createWrapper(
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/login'],
) {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <MemoryRouter initialEntries={initialEntries}>
      {children}
      <Link
        to="/login"
        state={{
          referrer: {
            pathname: '/1',
            search: '?filter=unassigned',
          },
        }}
      >
        emulate redirection
      </Link>
      <LocationLog />
    </MemoryRouter>
  );
  return Wrapper;
}

describe('<Login />', () => {
  beforeEach(() => {
    authenticationStore.disableSession();
  });

  afterEach(() => {
    authenticationStore.reset();
  });

  it('should redirect to the initial page on success', async () => {
    nodeMockServer.use(
      http.post(
        '/login',
        () => {
          return new HttpResponse('');
        },
        {
          once: true,
        },
      ),
    );

    const {user} = render(<Component />, {
      wrapper: createWrapper(),
    });

    await user.type(screen.getByLabelText(/username/i), 'demo');
    await user.type(screen.getByLabelText('Password'), 'demo');
    fireEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      screen.getByRole('button', {name: 'Logging in'}),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('button', {name: 'Login'}),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/i),
    );
  });

  it('should redirect to the referrer page', async () => {
    nodeMockServer.use(
      http.post(
        '/login',
        () => {
          return new HttpResponse('');
        },
        {
          once: true,
        },
      ),
    );
    const {user} = render(<Component />, {
      wrapper: createWrapper(),
    });

    await user.click(screen.getByRole('link', {name: /emulate redirection/i}));

    await user.type(screen.getByLabelText(/username/i), 'demo');
    await user.type(screen.getByLabelText('Password'), 'demo');
    await user.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent('/1'),
    );
    expect(screen.getByTestId('search')).toHaveTextContent('filter=unassigned');
  });

  it('should show an error for wrong credentials', async () => {
    nodeMockServer.use(
      http.post(
        '/login',
        () => {
          return new HttpResponse('', {
            status: 401,
          });
        },
        {
          once: true,
        },
      ),
    );
    const {user} = render(<Component />, {
      wrapper: createWrapper(),
    });

    await user.type(screen.getByLabelText(/username/i), 'demo');
    await user.type(screen.getByLabelText('Password'), 'demo');
    await user.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      await screen.findByText('Username and password do not match'),
    ).toBeInTheDocument();
  });

  it('should show a generic error message', async () => {
    nodeMockServer.use(
      http.post(
        '/login',
        () => {
          return new HttpResponse('', {
            status: 404,
          });
        },
        {
          once: true,
        },
      ),
    );
    const {user} = render(<Component />, {
      wrapper: createWrapper(),
    });

    await user.type(screen.getByLabelText(/username/i), 'demo');
    await user.type(screen.getByLabelText('Password'), 'demo');
    await user.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      await screen.findByText('Credentials could not be verified'),
    ).toBeInTheDocument();

    nodeMockServer.use(
      http.post('/login', () => {
        return HttpResponse.error();
      }),
    );

    await user.type(screen.getByLabelText(/username/i), 'demo');
    await user.type(screen.getByLabelText('Password'), 'demo');
    await user.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      await screen.findByText('Credentials could not be verified'),
    ).toBeInTheDocument();
  });

  it('should show a loading state while the login form is submitting', async () => {
    nodeMockServer.use(
      http.post(
        '/login',
        () => {
          return new HttpResponse('');
        },
        {
          once: true,
        },
      ),
    );

    const {user} = render(<Component />, {
      wrapper: createWrapper(),
    });

    await user.type(screen.getByLabelText(/username/i), 'demo');
    await user.type(screen.getByLabelText('Password'), 'demo');
    fireEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      screen.getByRole('button', {
        name: 'Logging in',
      }),
    ).toBeDisabled();
  });

  it('should have the correct copyright notice', () => {
    vi.useFakeTimers();
    const mockYear = 1984;
    vi.setSystemTime(new Date(mockYear, 0));
    render(<Component />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.getByText(
        `© Camunda Services GmbH ${mockYear}. All rights reserved. | 1.2.3`,
      ),
    ).toBeInTheDocument();
    vi.useRealTimers();
  });

  it('should not allow the form to be submitted with empty fields', async () => {
    nodeMockServer.use(
      http.post(
        '/login',
        () => {
          return new HttpResponse('');
        },
        {
          once: true,
        },
      ),
      http.post(
        '/login',
        () => {
          return new HttpResponse('');
        },
        {
          once: true,
        },
      ),
    );

    const {user} = render(<Component />, {
      wrapper: createWrapper(),
    });

    await user.click(screen.getByRole('button', {name: /login/i}));

    expect(screen.getByLabelText(/username/i)).toHaveAccessibleDescription(
      /username is required/i,
    );
    expect(screen.getByLabelText(/username/i)).toBeInvalid();
    expect(screen.getByLabelText(/^password$/i)).toHaveAccessibleDescription(
      /password is required/i,
    );
    expect(screen.getByLabelText(/^password$/i)).toBeInvalid();
    expect(screen.getByTestId('pathname')).toHaveTextContent('/login');

    await user.type(screen.getByLabelText(/username/i), 'demo');
    await user.click(screen.getByRole('button', {name: /login/i}));

    expect(
      screen.getByLabelText(/^password$/i),
    ).not.toHaveAccessibleDescription(/username is required/i);
    expect(screen.getByLabelText(/username/i)).toBeValid();
    expect(screen.getByLabelText(/^password$/i)).toHaveAccessibleDescription(
      /password is required/i,
    );
    expect(screen.getByLabelText(/^password$/i)).toBeInvalid();
    expect(screen.getByTestId('pathname')).toHaveTextContent('/login');

    await user.clear(screen.getByLabelText(/username/i));
    await user.type(screen.getByLabelText(/^password$/i), 'demo');
    await user.click(screen.getByRole('button', {name: /login/i}));

    expect(
      screen.getByLabelText(/^password$/i),
    ).not.toHaveAccessibleDescription(/password is required/i);
    expect(screen.getByLabelText(/^password$/i)).toBeValid();
    expect(screen.getByLabelText(/username/i)).toHaveAccessibleDescription(
      /username is required/i,
    );
    expect(screen.getByLabelText(/username/i)).toBeInvalid();
    expect(screen.getByTestId('pathname')).toHaveTextContent('/login');

    await user.type(screen.getByLabelText(/username/i), 'demo');
    fireEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/login');
    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/i),
    );
  });
});
