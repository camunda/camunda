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
import {AppHeader} from '../index';
import {authenticationStore} from 'modules/stores/authentication';
import {mockLogout} from 'modules/mocks/api/logout';
import {mockGetUser} from 'modules/mocks/api/getUser';
import {createUser} from 'modules/testUtils';
import {Wrapper as BaseWrapper} from './mocks';
import {useEffect} from 'react';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return authenticationStore.reset;
  }, []);
  return <BaseWrapper>{children}</BaseWrapper>;
};

const mockUser = createUser({
  displayName: 'Franz Kafka',
  canLogout: true,
});

const mockSsoUser = createUser({
  displayName: 'Michael Jordan',
  canLogout: false,
});

describe('User info', () => {
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

  it('should render links', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = jest.fn();
    window.open = mockOpenFn;

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

    await user.click(screen.getByRole('button', {name: 'Terms of use'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/',
      '_blank'
    );

    await user.click(screen.getByRole('button', {name: 'Privacy policy'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/legal/privacy/',
      '_blank'
    );

    await user.click(screen.getByRole('button', {name: 'Imprint'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/legal/imprint/',
      '_blank'
    );

    expect(
      screen.queryByRole('button', {name: 'Cookie preferences'})
    ).not.toBeInTheDocument();

    window.open = originalWindowOpen;
  });

  it('should cookie preferences with correct link', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = jest.fn();
    const mockShowDrawer = jest.fn();

    window.open = mockOpenFn;
    window.Osano = {
      cm: {
        analytics: false,
        showDrawer: mockShowDrawer,
        addEventListener: jest.fn(),
      },
    };

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

    await user.click(screen.getByRole('button', {name: 'Cookie preferences'}));

    expect(mockShowDrawer).toHaveBeenLastCalledWith(
      'osano-cm-dom-info-dialog-open'
    );

    window.open = originalWindowOpen;
    window.Osano = undefined;
  });
});
