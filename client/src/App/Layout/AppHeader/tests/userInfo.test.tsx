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
} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {AppHeader} from '../index';
import {authenticationStore} from 'modules/stores/authentication';
import {mockLogout} from 'modules/mocks/api/logout';
import {mockGetUser} from 'modules/mocks/api/getUser';
import {createUser} from 'modules/testUtils';
import {MemoryRouter} from 'react-router-dom';

const mockUser = createUser({
  displayName: 'Franz Kafka',
  canLogout: true,
});

const mockSsoUser = createUser({
  displayName: 'Michael Jordan',
  canLogout: false,
});

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter>{children} </MemoryRouter>
    </ThemeProvider>
  );
};

describe('User info', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should render user display name', async () => {
    mockGetUser().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    authenticationStore.authenticate();

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      })
    );

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();
  });

  it('should handle a SSO user', async () => {
    mockGetUser().withSuccess(mockSsoUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    authenticationStore.authenticate();

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      })
    );

    expect(await screen.findByText('Michael Jordan')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {
        name: /log out/i,
      })
    ).not.toBeInTheDocument();
  });

  it('should handle logout', async () => {
    mockLogout().withSuccess(null);
    mockGetUser().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    authenticationStore.authenticate();

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      })
    );

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {
        name: /log out/i,
      })
    );

    await user.click(
      screen.getByRole('button', {
        name: /settings/i,
      })
    );

    await waitForElementToBeRemoved(() => screen.getByText('Franz Kafka'));
  });
});
