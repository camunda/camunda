/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
  waitFor,
} from 'modules/testing-library';
import {Link, MemoryRouter, To} from 'react-router-dom';
import {Login} from './index';
import {LOGIN_ERROR, GENERIC_ERROR} from './constants';
import {LocationLog} from 'modules/utils/LocationLog';
import {authenticationStore} from 'modules/stores/authentication';
import {mockLogin} from 'modules/mocks/api/login';
import {Paths} from 'modules/Routes';

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
    await user.click(screen.getByRole('button', {name: 'Login'}));

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('spinner'));

    mockLogin().withSuccess(null);

    await user.click(screen.getByRole('button', {name: 'Login'}));

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('spinner'));
  });

  it('should redirect to the previous page', async () => {
    mockLogin().withSuccess(null);

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
    await user.click(screen.getByRole('button', {name: /login/i}));

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
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

    mockLogin().withNetworkError();

    const {user} = render(<Login />, {
      wrapper: createWrapper(),
    });

    await user.type(screen.getByLabelText(/^username$/i), 'demo');
    await user.type(screen.getByLabelText(/^password$/i), 'demo');
    await user.click(screen.getByRole('button', {name: 'Login'}));

    expect(await screen.findByText(GENERIC_ERROR)).toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });
});
