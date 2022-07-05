/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
  waitFor,
} from 'modules/testing-library';
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
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
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

    const {user} = render(<Login />, {
      wrapper: createWrapper('/login'),
    });

    await user.type(screen.getByLabelText(/username/i), 'demo');
    await user.type(screen.getByLabelText(/password/i), 'demo');
    await user.click(screen.getByRole('button', {name: /log in/i}));

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/)
    );
  });

  it.skip('should show a loading spinner', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.text(''))
      )
    );

    const {user} = render(<Login />, {
      wrapper: createWrapper(),
    });

    await user.type(screen.getByLabelText(/username/i), 'demo');
    await user.type(screen.getByLabelText(/password/i), 'demo');
    await user.click(screen.getByRole('button', {name: /log in/i}));

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('spinner'));

    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text('')))
    );

    await user.click(screen.getByRole('button', {name: /log in/i}));

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('spinner'));
  });

  it('should redirect to the previous page', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text('')))
    );

    const {user} = render(<Login />, {
      wrapper: createWrapper('/login'),
    });

    await user.click(screen.getByText(/emulate auth check/i));

    await user.type(screen.getByLabelText(/username/i), 'demo');
    await user.type(screen.getByLabelText(/password/i), 'demo');
    await user.click(screen.getByRole('button', {name: /log in/i}));

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/)
    );
  });

  it('should disable the login button when any field is empty', async () => {
    const {user} = render(<Login />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByRole('button', {name: /log in/i})).toBeDisabled();

    await user.type(screen.getByLabelText(/username/i), 'demo');

    expect(screen.getByRole('button', {name: /log in/i})).toBeDisabled();

    await user.type(screen.getByLabelText(/password/i), 'demo');

    expect(screen.getByRole('button', {name: /log in/i})).toBeEnabled();

    await user.clear(screen.getByLabelText(/password/i));
    await user.clear(screen.getByLabelText(/username/i));

    expect(screen.getByRole('button', {name: /log in/i})).toBeDisabled();
  });

  it('should handle wrong credentials', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) =>
        res.once(ctx.status(401), ctx.text(''))
      )
    );

    const {user} = render(<Login />, {
      wrapper: createWrapper(),
    });

    await user.type(screen.getByLabelText(/username/i), 'wrong');
    await user.type(screen.getByLabelText(/password/i), 'credentials');
    await user.click(screen.getByRole('button', {name: /log in/i}));

    expect(await screen.findByText(LOGIN_ERROR)).toBeInTheDocument();
  });

  it('should handle generic errors', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.text(''))
      )
    );

    const {user} = render(<Login />, {
      wrapper: createWrapper(),
    });

    await user.type(screen.getByLabelText(/username/i), 'demo');
    await user.type(screen.getByLabelText(/password/i), 'demo');
    await user.click(screen.getByRole('button', {name: /log in/i}));

    expect(await screen.findByText(GENERIC_ERROR)).toBeInTheDocument();
  });

  it('should handle request failures', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res) => res.networkError('Request failed'))
    );

    const {user} = render(<Login />, {
      wrapper: createWrapper(),
    });

    await user.type(screen.getByLabelText(/username/i), 'demo');
    await user.type(screen.getByLabelText(/password/i), 'demo');
    await user.click(screen.getByRole('button', {name: /log in/i}));

    expect(await screen.findByText(GENERIC_ERROR)).toBeInTheDocument();
  });
});
