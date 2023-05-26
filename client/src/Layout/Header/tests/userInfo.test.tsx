/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {DEFAULT_MOCK_CLIENT_CONFIG} from 'modules/mocks/window';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {authenticationStore} from 'modules/stores/authentication';
import {rest} from 'msw';
import {Header} from '..';
import {Wrapper} from './mocks';
import * as userMocks from 'modules/mock-schema/mocks/current-user';

describe('User info', () => {
  beforeAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = true;
  });

  afterEach(() => {
    window.clientConfig = DEFAULT_MOCK_CLIENT_CONFIG;
  });

  it('should render user display name', async () => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
    );

    const {user} = render(<Header />, {
      wrapper: Wrapper,
    });

    await user.click(
      await screen.findByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
  });

  it('should handle a SSO user', async () => {
    window.clientConfig = {...window.clientConfig, canLogout: false};

    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
    );

    const {user} = render(<Header />, {
      wrapper: Wrapper,
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
    const logoutSpy = jest
      .spyOn(authenticationStore, 'handleLogout')
      .mockImplementation();

    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
      rest.post('/api/logout', (_, res, ctx) =>
        res.once(ctx.status(204), ctx.json('')),
      ),
    );

    const {user} = render(<Header />, {
      wrapper: Wrapper,
    });

    await user.click(
      screen.getByRole('button', {
        name: /settings/i,
      }),
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    await user.click(screen.getByText('Log out'));

    expect(logoutSpy).toHaveBeenCalled();
    logoutSpy.mockRestore();
  });

  it('should render links', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = jest.fn();
    window.open = mockOpenFn;

    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
    );

    const {user} = render(<Header />, {
      wrapper: Wrapper,
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

    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
      }),
    );

    const {user} = render(<Header />, {
      wrapper: Wrapper,
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
});
