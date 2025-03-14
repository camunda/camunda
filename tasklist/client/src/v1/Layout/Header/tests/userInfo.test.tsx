/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'common/testing/testing-library';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {authenticationStore} from 'common/auth/authentication';
import {http, HttpResponse} from 'msw';
import {Header} from '..';
import {getWrapper} from './mocks';
import * as userMocks from 'common/mocks/current-user';
import * as licenseMocks from 'common/mocks/license';
import {getClientConfig} from 'common/config/getClientConfig';

vi.mock('common/config/getClientConfig', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('common/config/getClientConfig')>();
  return {
    getClientConfig: vi.fn().mockImplementation(actual.getClientConfig),
  };
});

const {getClientConfig: actualGetClientConfig} = await vi.importActual<
  typeof import('common/config/getClientConfig')
>('common/config/getClientConfig');
const mockGetClientConfig = vi.mocked(getClientConfig);

describe('User info', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v2/license',
        () => {
          return HttpResponse.json(licenseMocks.saasLicense);
        },
        {
          once: true,
        },
      ),
    );

    mockGetClientConfig.mockReturnValue(actualGetClientConfig());
  });

  it('should render user display name', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {
          once: true,
        },
      ),
    );

    const {user} = render(<Header />, {
      wrapper: getWrapper(),
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
  });

  it('should render language selection dropdown', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {
          once: true,
        },
      ),
    );

    const {user} = render(<Header />, {
      wrapper: getWrapper(),
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    const languageDropdown = await screen.findByRole('combobox', {
      name: 'Language',
    });

    expect(languageDropdown).toBeInTheDocument();
    expect(languageDropdown).toHaveTextContent('English');
  });

  it('should handle a SSO user', async () => {
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      canLogout: false,
    });

    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {
          once: true,
        },
      ),
    );

    const {user} = render(<Header />, {
      wrapper: getWrapper(),
    });

    await user.click(
      screen.getByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {
        name: /log out/i,
      }),
    ).not.toBeInTheDocument();
  });

  it('should handle logout', async () => {
    const logoutSpy = vi.spyOn(authenticationStore, 'handleLogout');

    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {
          once: true,
        },
      ),
      http.post(
        '/logout',
        () => {
          return new HttpResponse(null, {
            status: 204,
          }).json();
        },
        {
          once: true,
        },
      ),
    );

    const {user} = render(<Header />, {
      wrapper: getWrapper(),
    });

    await user.click(
      screen.getByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    await user.click(screen.getByText('Log out'));

    expect(logoutSpy).toHaveBeenCalled();
  });

  it('should render links', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = vi.fn();
    window.open = mockOpenFn;

    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {
          once: true,
        },
      ),
    );

    const {user} = render(<Header />, {
      wrapper: getWrapper(),
    });

    await user.click(
      screen.getByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    await user.click(screen.getByText('Terms of use'));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/',
      '_blank',
    );

    await user.click(screen.getByText('Privacy policy'));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/legal/privacy/',
      '_blank',
    );

    await user.click(screen.getByText('Imprint'));
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
    const mockOpenFn = vi.fn();
    const mockShowDrawer = vi.fn();

    window.open = mockOpenFn;
    window.Osano = {
      cm: {
        analytics: false,
        showDrawer: mockShowDrawer,
        addEventListener: vi.fn(),
      },
    };

    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUser);
        },
        {
          once: true,
        },
      ),
    );

    const {user} = render(<Header />, {
      wrapper: getWrapper(),
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    await user.click(screen.getByText('Cookie preferences'));

    expect(mockShowDrawer).toHaveBeenLastCalledWith(
      'osano-cm-dom-info-dialog-open',
    );

    window.open = originalWindowOpen;
    window.Osano = undefined;
  });

  it('should hide nav links if application is unauthorized', async () => {
    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(userMocks.currentUnauthorizedUser);
        },
        {
          once: true,
        },
      ),
    );

    render(<Header />, {
      wrapper: getWrapper(),
    });

    await waitFor(() => {
      expect(screen.queryByText('Tasks')).not.toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.queryByText('Processes')).not.toBeInTheDocument();
    });
  });
});
