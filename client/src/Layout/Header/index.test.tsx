/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, fireEvent, waitFor} from '@testing-library/react';
import {Header} from './index';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {MemoryRouter} from 'react-router-dom';
import {authenticationStore} from 'modules/stores/authentication';
import {mockGetCurrentUser} from 'modules/queries/get-current-user';
import {rest, graphql} from 'msw';
import {mockServer} from 'modules/mockServer';
import {ApolloProvider} from '@apollo/client';
import {client} from 'modules/apollo-client';
import {LocationLog} from 'modules/utils/LocationLog';

function createWrapper(
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = ['/'],
) {
  type Props = {
    children?: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <ApolloProvider client={client}>
        <MockThemeProvider>
          <MemoryRouter initialEntries={initialEntries}>
            {children}
            <LocationLog />
          </MemoryRouter>
        </MockThemeProvider>
      </ApolloProvider>
    );
  };
  return Wrapper;
}

describe('<Header />', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  beforeEach(() => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
    );
  });

  it('should render header', async () => {
    render(<Header />, {
      wrapper: createWrapper(),
    });
    expect(screen.getByText('Tasklist')).toBeInTheDocument();
    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.getByTestId('logo')).toBeInTheDocument();
    expect(screen.getByTestId('dropdown-icon')).toBeInTheDocument();
  });

  it('should navigate to home page when brand label is clicked', async () => {
    render(<Header />, {
      wrapper: createWrapper(),
    });
    await screen.findByText('Demo User');

    fireEvent.click(screen.getByText('Tasklist'));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/');
    expect(screen.getByTestId('search')).toHaveTextContent('');
  });

  it('should handle logout', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res) => res.once()),
      rest.post('/api/logout', (_, res) => res.once()),
    );
    await authenticationStore.handleLogin('demo', 'demo');

    render(<Header />, {
      wrapper: createWrapper(),
    });

    expect(authenticationStore.status).toBe('logged-in');

    fireEvent.click(await screen.findByText('Demo User'));
    fireEvent.click(screen.getByText('Logout'));

    await waitFor(() => expect(authenticationStore.status).toBe('logged-out'));
    expect(screen.queryByText('logout')).not.toBeInTheDocument();
  });

  describe('license note', () => {
    beforeEach(() => {
      Object.defineProperties(window.clientConfig, {
        isEnterprise: {
          configurable: true,
          writable: true,
          value: null,
        },
        organizationId: {
          configurable: true,
          writable: true,
          value: null,
        },
      });
    });
    afterEach(() => {
      delete window.clientConfig!.isEnterprise;
      delete window.clientConfig!.organizationId;
    });

    it('should show license note in CCSM free/trial environment', () => {
      window.clientConfig!.isEnterprise = false;

      render(<Header />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByText('Non-Production License')).toBeInTheDocument();
    });

    it('should not show license note in SaaS environment', () => {
      window.clientConfig!.isEnterprise = false;
      window.clientConfig!.organizationId =
        '000000000-0000-0000-0000-000000000000';

      render(<Header />, {
        wrapper: createWrapper(),
      });

      expect(
        screen.queryByText('Non-Production License'),
      ).not.toBeInTheDocument();
    });

    it('should not show license note in CCSM enterprise environment', () => {
      window.clientConfig!.isEnterprise = true;

      render(<Header />, {
        wrapper: createWrapper(),
      });

      expect(
        screen.queryByText('Non-Production License'),
      ).not.toBeInTheDocument();
    });
  });
});
