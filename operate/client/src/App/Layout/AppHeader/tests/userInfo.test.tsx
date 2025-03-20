/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act, render, screen, waitFor} from 'modules/testing-library';
import {AppHeader} from '../index';
import {authenticationStore} from 'modules/stores/authentication';
import {mockLogout} from 'modules/mocks/api/logout';
import {mockMe} from 'modules/mocks/api/v2/me';
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
    mockMe().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await act(async () => {
      await authenticationStore.authenticate();
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();
  });

  it('should handle a SSO user', async () => {
    mockMe().withSuccess(mockSsoUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await act(async () => {
      await authenticationStore.authenticate();
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Michael Jordan')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {
        name: /log out/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should handle logout', async () => {
    mockLogout().withSuccess(null);
    mockMe().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await act(async () => {
      await authenticationStore.authenticate();
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {
        name: /log out/i,
      }),
    );

    await user.click(
      screen.getByRole('button', {
        name: /settings/i,
      }),
    );

    await waitFor(() =>
      expect(screen.queryByText('Franz Kafka')).not.toBeInTheDocument(),
    );
  });

  it('should render links', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = jest.fn();
    window.open = mockOpenFn;

    mockMe().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await act(async () => {
      await authenticationStore.authenticate();
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Terms of use'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );
    await user.click(screen.getByRole('button', {name: 'Privacy policy'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/legal/privacy/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );
    await user.click(screen.getByRole('button', {name: 'Imprint'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/legal/imprint/',
      '_blank',
    );

    expect(
      screen.queryByRole('button', {name: 'Cookie preferences'}),
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

    mockMe().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await act(async () => {
      await authenticationStore.authenticate();
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {name: 'Cookie preferences'}),
    );

    expect(mockShowDrawer).toHaveBeenLastCalledWith(
      'osano-cm-dom-info-dialog-open',
    );

    window.open = originalWindowOpen;
    window.Osano = undefined;
  });

  it('should hide nav links if operate is unauthorized', async () => {
    const mockUser = createUser({
      displayName: 'Franz Kafka',
      canLogout: true,
      authorizedApplications: ['tasklist'],
    });

    mockMe().withSuccess(mockUser);

    render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await act(async () => {
      await authenticationStore.authenticate();
    });

    expect(screen.queryByText('Dashboard')).not.toBeInTheDocument();
    expect(screen.queryByText('Processes')).not.toBeInTheDocument();
    expect(screen.queryByText('Decisions')).not.toBeInTheDocument();
  });
});
