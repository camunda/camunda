/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {
  mockGetCurrentUser,
  mockGetCurrentRestrictedUser,
} from 'modules/queries/get-current-user';
import {render, screen, within} from 'modules/testing-library';
import {graphql} from 'msw';
import {Header} from '..';
import {Wrapper} from './mocks';
import {DEFAULT_MOCK_CLIENT_CONFIG} from 'modules/mocks/window';

describe('processes tab', () => {
  beforeEach(() => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
    );
  });

  afterEach(() => {
    window.clientConfig = DEFAULT_MOCK_CLIENT_CONFIG;
    process.env.REACT_APP_VERSION = '1.2.3';
  });

  it('should render for self managed user when stable', async () => {
    window.clientConfig = {
      ...window.clientConfig,
      isResourcePermissionsEnabled: true,
    };

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    expect(
      within(
        screen.getByRole('navigation', {
          name: 'Camunda Tasklist',
        }),
      ).getByRole('link', {
        name: 'Processes',
      }),
    ).toBeInTheDocument();
  });

  it('should render for self managed users when alpha', async () => {
    window.clientConfig = {
      ...window.clientConfig,
      isResourcePermissionsEnabled: true,
    };
    process.env.REACT_APP_VERSION = '0.0.0-alpha0';

    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
    );

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    expect(
      within(
        screen.getByRole('navigation', {
          name: 'Camunda Tasklist',
        }),
      ).getByRole('link', {
        name: 'Processes',
      }),
    ).toBeInTheDocument();
  });

  it('should not render for self managed users when restricted', async () => {
    window.clientConfig = {
      ...window.clientConfig,
      isResourcePermissionsEnabled: true,
    };
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser));
      }),
    );

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    const withinNavigation = within(
      screen.getByRole('navigation', {
        name: 'Camunda Tasklist',
      }),
    );

    expect(
      withinNavigation.queryByRole('link', {
        name: 'Processes',
      }),
    ).not.toBeInTheDocument();
  });

  it('should not render for self managed without resource permissions', async () => {
    window.clientConfig = {
      ...window.clientConfig,
      isResourcePermissionsEnabled: false,
    };
    process.env.REACT_APP_VERSION = '0.0.0';

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    const withinNavigation = within(
      screen.getByRole('navigation', {
        name: 'Camunda Tasklist',
      }),
    );

    expect(
      withinNavigation.queryByRole('link', {
        name: 'Processes',
      }),
    ).not.toBeInTheDocument();
  });

  it('should render for saas users when alpha', async () => {
    window.clientConfig = {
      ...window.clientConfig,
      organizationId: '1-1-1',
      isResourcePermissionsEnabled: true,
    };

    process.env.REACT_APP_VERSION = '0.0.0-alpha0';

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    expect(
      within(
        screen.getByRole('navigation', {
          name: 'Camunda Tasklist',
        }),
      ).getByRole('link', {
        name: 'Processes',
      }),
    ).toBeInTheDocument();
  });

  it('should not render for saas users when stable', async () => {
    window.clientConfig = {
      ...window.clientConfig,
      organizationId: '1-1-1',
      isResourcePermissionsEnabled: true,
    };
    process.env.REACT_APP_VERSION = '0.0.0';

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    const withinNavigation = within(
      screen.getByRole('navigation', {
        name: 'Camunda Tasklist',
      }),
    );

    expect(
      withinNavigation.queryByRole('link', {
        name: 'Processes',
      }),
    ).not.toBeInTheDocument();
  });

  it('should not render for saas users when restricted', async () => {
    window.clientConfig = {
      ...window.clientConfig,
      organizationId: '1-1-1',
      isResourcePermissionsEnabled: true,
    };
    process.env.REACT_APP_VERSION = '0.0.0-alpha0';

    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser));
      }),
    );

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    const withinNavigation = within(
      screen.getByRole('navigation', {
        name: 'Camunda Tasklist',
      }),
    );

    expect(
      withinNavigation.queryByRole('link', {
        name: 'Processes',
      }),
    ).not.toBeInTheDocument();
  });

  it('should not care about resource permissions on saas', async () => {
    window.clientConfig = {
      ...window.clientConfig,
      isResourcePermissionsEnabled: false,
    };
    process.env.REACT_APP_VERSION = '0.0.0';

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    expect(
      within(
        screen.getByRole('navigation', {
          name: 'Camunda Tasklist',
        }),
      ).getByRole('link', {
        name: 'Processes',
      }),
    ).toBeInTheDocument();
  });
});
