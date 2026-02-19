/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {AppHeader} from '../index';
import {mockLogout} from 'modules/mocks/api/logout';
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser} from 'modules/testUtils';
import {Wrapper as BaseWrapper} from './mocks';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {authenticationStore} from 'modules/stores/authentication';
import {getClientConfig} from 'modules/utils/getClientConfig';

vi.mock('modules/utils/getClientConfig', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('modules/utils/getClientConfig')>();
  return {
    getClientConfig: vi.fn().mockImplementation(actual.getClientConfig),
  };
});

const {getClientConfig: actualGetClientConfig} = await vi.importActual<
  typeof import('modules/utils/getClientConfig')
>('modules/utils/getClientConfig');

const mockGetClientConfig = vi.mocked(getClientConfig);

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <BaseWrapper>{children}</BaseWrapper>
    </QueryClientProvider>
  );
};

const mockUser = createUser({
  displayName: 'Franz Kafka',
  canLogout: true,
  authorizedComponents: ['operate'],
});

const mockSsoUser = createUser({
  displayName: 'Michael Jordan',
  canLogout: false,
});

describe('User info', () => {
  beforeEach(() => {
    mockGetClientConfig.mockReturnValue(actualGetClientConfig());
  });

  it('should render user display name', async () => {
    mockMe().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();
  });

  it('should handle a SSO user', async () => {
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      canLogout: false,
    });
    mockMe().withSuccess(mockSsoUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
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
    const logoutSpy = vi.spyOn(authenticationStore, 'handleLogout');
    mockLogout().withSuccess(null);
    mockMe().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
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

    await waitFor(() => expect(logoutSpy).toHaveBeenCalled());
  });

  it('should render links', async () => {
    const mockOpenFn = vi.fn();
    vi.stubGlobal('open', mockOpenFn);

    mockMe().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
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
  });

  it('should cookie preferences with correct link', async () => {
    const mockOpenFn = vi.fn();
    vi.stubGlobal('open', mockOpenFn);
    const mockShowDrawer = vi.fn();
    vi.stubGlobal('Osano', {
      cm: {
        analytics: false,
        showDrawer: mockShowDrawer,
        addEventListener: vi.fn(),
      },
    });

    mockMe().withSuccess(mockUser);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
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
  });

  it('should hide nav links if operate is unauthorized', async () => {
    const mockUser = createUser({
      displayName: 'Franz Kafka',
      canLogout: true,
      authorizedComponents: ['tasklist'],
    });

    mockMe().withSuccess(mockUser);

    render(<AppHeader />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Franz Kafka')).toBeInTheDocument();

    expect(screen.queryByText('Dashboard')).not.toBeInTheDocument();
    expect(screen.queryByText('Processes')).not.toBeInTheDocument();
    expect(screen.queryByText('Decisions')).not.toBeInTheDocument();
  });
});
