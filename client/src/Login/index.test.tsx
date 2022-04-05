/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {Link, MemoryRouter} from 'react-router-dom';
import {rest} from 'msw';
import {Login} from './index';
import {authenticationStore} from 'modules/stores/authentication';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {mockServer} from 'modules/mockServer';
import {LocationLog} from 'modules/utils/LocationLog';

const getFullYearMock = jest.spyOn(Date.prototype, 'getFullYear');
function createWrapper(
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/login'],
) {
  const Wrapper: React.FC = ({children}) => (
    <MockThemeProvider>
      <MemoryRouter initialEntries={initialEntries}>
        {children}
        <Link
          to="/login"
          state={{
            referrer: {
              pathname: '/1',
              search: '?filter=unclaimed',
            },
          }}
        >
          emulate redirection
        </Link>
        <LocationLog />
      </MemoryRouter>
    </MockThemeProvider>
  );
  return Wrapper;
}

describe('<Login />', () => {
  afterEach(() => {
    getFullYearMock.mockClear();
    authenticationStore.reset();
  });

  afterAll(() => {
    getFullYearMock.mockRestore();
  });

  it('should redirect to the initial page on success', async () => {
    authenticationStore.disableSession();
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text(''))),
    );

    render(<Login />, {
      wrapper: createWrapper(),
    });

    userEvent.type(screen.getByLabelText('Username'), 'demo');
    userEvent.type(screen.getByLabelText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent('/'),
    );
  });

  it('should redirect to the referrer page', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text(''))),
    );
    authenticationStore.disableSession();
    render(<Login />, {
      wrapper: createWrapper(),
    });

    userEvent.click(screen.getByRole('link', {name: /emulate redirection/i}));

    userEvent.type(screen.getByLabelText('Username'), 'demo');
    userEvent.type(screen.getByLabelText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent('/1'),
    );
    expect(screen.getByTestId('search')).toHaveTextContent('filter=unclaimed');
  });

  it('should show an error for wrong credentials', async () => {
    authenticationStore.disableSession();
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) =>
        res.once(ctx.status(401), ctx.text('')),
      ),
    );
    render(<Login />, {
      wrapper: createWrapper(),
    });

    userEvent.type(screen.getByLabelText('Username'), 'demo');
    userEvent.type(screen.getByLabelText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      await screen.findByText('Username and Password do not match'),
    ).toBeInTheDocument();
  });

  it('should show a generic error message', async () => {
    authenticationStore.disableSession();
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) =>
        res.once(ctx.status(404), ctx.text('')),
      ),
    );
    render(<Login />, {
      wrapper: createWrapper(),
    });

    userEvent.type(screen.getByLabelText('Username'), 'demo');
    userEvent.type(screen.getByLabelText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      await screen.findByText('Credentials could not be verified'),
    ).toBeInTheDocument();

    mockServer.use(
      rest.post('/api/login', (_, res) => res.networkError('A network error')),
    );

    userEvent.type(screen.getByLabelText('Username'), 'demo');
    userEvent.type(screen.getByLabelText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      await screen.findByText('Credentials could not be verified'),
    ).toBeInTheDocument();
  });

  it('should show a loading overlay while the login form is submitting', async () => {
    authenticationStore.disableSession();
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text(''))),
    );

    render(<Login />, {
      wrapper: createWrapper(),
    });

    userEvent.type(screen.getByLabelText('Username'), 'demo');
    userEvent.type(screen.getByLabelText('Password'), 'demo');
    userEvent.click(screen.getByRole('button', {name: 'Login'}));

    expect(
      await screen.findByTestId('login-loading-overlay'),
    ).toBeInTheDocument();
  });

  it('should have the correct copyright notice', () => {
    const mockYear = 1984;
    getFullYearMock.mockReturnValue(mockYear);
    authenticationStore.disableSession();
    render(<Login />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.getByText(
        `© Camunda Services GmbH ${mockYear}. All rights reserved. | 1.2.3`,
      ),
    ).toBeInTheDocument();
  });

  it('should not allow the form to be submitted with empty fields', async () => {
    authenticationStore.disableSession();

    render(<Login />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByRole('button', {name: 'Login'})).toBeDisabled();

    userEvent.type(screen.getByLabelText('Username'), 'demo');

    expect(screen.getByRole('button', {name: 'Login'})).toBeDisabled();

    userEvent.type(screen.getByLabelText('Password'), 'demo');

    expect(screen.getByRole('button', {name: 'Login'})).toBeEnabled();

    userEvent.clear(screen.getByLabelText('Username'));

    expect(screen.getByRole('button', {name: 'Login'})).toBeDisabled();

    userEvent.clear(screen.getByLabelText('Password'));

    expect(screen.getByRole('button', {name: 'Login'})).toBeDisabled();
  });
});
