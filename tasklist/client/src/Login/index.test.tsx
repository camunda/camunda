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

import {fireEvent, render, screen, waitFor} from 'modules/testing-library';
import {Link, MemoryRouter} from 'react-router-dom';
import {http, HttpResponse} from 'msw';
import {Component} from './index';
import {authenticationStore} from 'modules/stores/authentication';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {LocationLog} from 'modules/utils/LocationLog';

function createWrapper(
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/login'],
) {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <MockThemeProvider>
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
    </MockThemeProvider>
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
        '/api/login',
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
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/$/i);
  });

  it('should redirect to the referrer page', async () => {
    nodeMockServer.use(
      http.post(
        '/api/login',
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
        '/api/login',
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
        '/api/login',
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
      http.post('/api/login', () => {
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
        '/api/login',
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
        '/api/login',
        () => {
          return new HttpResponse('');
        },
        {
          once: true,
        },
      ),
      http.post(
        '/api/login',
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
