/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {User} from './index';
import {authenticationStore} from 'modules/stores/authentication';
import {mockLogout} from 'modules/mocks/api/logout';
import {mockGetUser} from 'modules/mocks/api/getUser';
import {createUser} from 'modules/testUtils';

const mockUser = createUser({
  displayName: 'Franz Kafka',
  canLogout: true,
});

const mockSsoUser = createUser({
  displayName: 'Michael Jordan',
  canLogout: false,
});

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return <ThemeProvider>{children}</ThemeProvider>;
};

describe('User', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should render user display name', async () => {
    mockGetUser().withSuccess(mockUser);

    render(<User />, {
      wrapper: Wrapper,
    });

    authenticationStore.authenticate();

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();
  });

  it('should handle a SSO user', async () => {
    mockGetUser().withSuccess(mockSsoUser);

    const {user} = render(<User />, {
      wrapper: Wrapper,
    });

    authenticationStore.authenticate();

    expect(await screen.findByText('Michael Jordan')).toBeInTheDocument();

    await user.click(await screen.findByText('Michael Jordan'));

    expect(screen.queryByText('Logout')).not.toBeInTheDocument();
  });

  it('should handle logout', async () => {
    mockLogout().withSuccess(null);
    mockGetUser().withSuccess(mockUser);

    const {user} = render(<User />, {
      wrapper: Wrapper,
    });

    authenticationStore.authenticate();

    await user.click(await screen.findByText('Franz Kafka'));
    await user.click(await screen.findByText('Logout'));

    expect(await screen.findByTestId('username-skeleton')).toBeInTheDocument();
  });
});
