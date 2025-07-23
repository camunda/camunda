/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
  waitFor,
  fireEvent,
} from 'modules/testing-library';
import {Link, MemoryRouter, type To} from 'react-router-dom';
import {Login} from './index';
import {LOGIN_ERROR, GENERIC_ERROR} from './constants';
import {LocationLog} from 'modules/utils/LocationLog';
import {authenticationStore} from 'modules/stores/authentication';
import {mockLogin} from 'modules/mocks/api/login';
import {Paths} from 'modules/Routes';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';

function createWrapper(
  initialPath: string = Paths.login(),
  referrer: To = {pathname: Paths.processes()},
) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <MemoryRouter initialEntries={[initialPath]}>
        {children}
        <Link
          to={Paths.login()}
          state={{
            referrer,
          }}
        >
          emulate auth check
        </Link>
        <LocationLog />
      </MemoryRouter>
    );
  };

  return Wrapper;
}

describe('<Login />', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should login', async () => {
    mockLogin().withSuccess(null);
    mockMe().withSuccess(createUser());

    const {user} = render(<Login />, {
      wrapper: createWrapper(Paths.login()),
    });

    await user.type(screen.getByLabelText(/^username$/i), 'demo');
    await user.type(screen.getByLabelText(/^password$/i), 'demo');
    await user.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/),
    );
  });

  it('should show a loading spinner', async () => {
    mockLogin().withServerError();

    const {user} = render(<Login />, {
      wrapper: createWrapper(),
    });

    await user.type(screen.getByLabelText(/^username$/i), 'demo');
    await user.type(screen.getByLabelText(/^password$/i), 'demo');
    fireEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.queryByTestId('spinner'));

    mockLogin().withSuccess(null);
    mockMe().withSuccess(createUser());

    fireEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.queryByTestId('spinner'));
  });

  it('should redirect to the previous page', async () => {
    mockLogin().withSuccess(null);
    mockMe().withSuccess(createUser());

    const {user} = render(<Login />, {
      wrapper: createWrapper(Paths.login()),
    });

    await user.click(screen.getByText(/emulate auth check/i));

    await user.type(screen.getByLabelText(/^username$/i), 'demo');
    await user.type(screen.getByLabelText(/^password$/i), 'demo');
    await user.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/),
    );
  });

  it('should not allow the form to be submitted with empty fields', async () => {
    mockLogin().withSuccess(null);
    mockMe().withSuccess(createUser());
    const {user} = render(<Login />, {
      wrapper: createWrapper(),
    });

    await user.click(screen.getByRole('button', {name: /login/i}));

    expect(screen.getByLabelText(/^username$/i)).toHaveAccessibleDescription(
      /username is required/i,
    );
    expect(screen.getByLabelText(/^username$/i)).toBeInvalid();
    expect(screen.getByLabelText(/^password$/i)).toHaveAccessibleDescription(
      /password is required/i,
    );
    expect(screen.getByLabelText(/^password$/i)).toBeInvalid();
    expect(screen.getByTestId('pathname')).toHaveTextContent('/login');

    await user.type(screen.getByLabelText(/^username$/i), 'demo');
    await user.click(screen.getByRole('button', {name: /login/i}));

    expect(
      screen.getByLabelText(/^password$/i),
    ).not.toHaveAccessibleDescription(/username is required/i);
    expect(screen.getByLabelText(/^username$/i)).toBeValid();
    expect(screen.getByLabelText(/^password$/i)).toHaveAccessibleDescription(
      /password is required/i,
    );
    expect(screen.getByLabelText(/^password$/i)).toBeInvalid();
    expect(screen.getByTestId('pathname')).toHaveTextContent('/login');

    await user.clear(screen.getByLabelText(/^username$/i));
    await user.type(screen.getByLabelText(/^password$/i), 'demo');
    await user.click(screen.getByRole('button', {name: /login/i}));

    expect(
      screen.getByLabelText(/^password$/i),
    ).not.toHaveAccessibleDescription(/password is required/i);
    expect(screen.getByLabelText(/^password$/i)).toBeValid();
    expect(screen.getByLabelText(/^username$/i)).toHaveAccessibleDescription(
      /username is required/i,
    );
    expect(screen.getByLabelText(/^username$/i)).toBeInvalid();
    expect(screen.getByTestId('pathname')).toHaveTextContent('/login');

    await user.type(screen.getByLabelText(/^username$/i), 'demo');
    fireEvent.click(screen.getByRole('button', {name: /login/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/login');
    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/i),
    );
  });

  it('should handle wrong credentials', async () => {
    mockLogin().withServerError(401);

    const {user} = render(<Login />, {
      wrapper: createWrapper(),
    });

    await user.type(screen.getByLabelText(/^username$/i), 'wrong');
    await user.type(screen.getByLabelText(/^password$/i), 'credentials');
    await user.click(screen.getByRole('button', {name: 'Login'}));

    expect(await screen.findByText(LOGIN_ERROR)).toBeInTheDocument();
  });

  it('should handle generic errors', async () => {
    mockLogin().withServerError();

    const {user} = render(<Login />, {
      wrapper: createWrapper(),
    });

    await user.type(screen.getByLabelText(/^username$/i), 'demo');
    await user.type(screen.getByLabelText(/^password$/i), 'demo');
    await user.click(screen.getByRole('button', {name: 'Login'}));

    expect(await screen.findByText(GENERIC_ERROR)).toBeInTheDocument();
  });

  it('should handle request failures', async () => {
    mockLogin().withNetworkError();

    const {user} = render(<Login />, {
      wrapper: createWrapper(),
    });

    await user.type(screen.getByLabelText(/^username$/i), 'demo');
    await user.type(screen.getByLabelText(/^password$/i), 'demo');
    await user.click(screen.getByRole('button', {name: 'Login'}));

    expect(await screen.findByText(GENERIC_ERROR)).toBeInTheDocument();
  });
});
