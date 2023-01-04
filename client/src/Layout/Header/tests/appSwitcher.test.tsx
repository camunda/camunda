/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, within} from '@testing-library/react';
import {mockServer} from 'modules/mockServer';
import {mockGetCurrentUserWithC8Links} from 'modules/queries/get-current-user';
import {graphql} from 'msw';
import {Header} from '..';
import {Wrapper} from './mocks';
import userEvent from '@testing-library/user-event';
import {DEFAULT_MOCK_CLIENT_CONFIG} from 'modules/mocks/window';

describe('App switcher', () => {
  afterEach(() => {
    window.clientConfig = DEFAULT_MOCK_CLIENT_CONFIG;
  });

  it('should render with correct links', async () => {
    window.clientConfig = {
      ...window.clientConfig,
      isEnterprise: false,
      organizationId: 'some-organization-id',
    };

    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res(ctx.data(mockGetCurrentUserWithC8Links.result.data));
      }),
    );

    render(<Header />, {
      wrapper: Wrapper,
    });

    userEvent.click(
      await screen.findByRole('button', {
        name: /app switcher/i,
      }),
    );

    const withinAppPanel = within(
      screen.getByRole('navigation', {
        name: /app panel/i,
      }),
    );

    expect(
      await withinAppPanel.findByRole('link', {name: 'Console'}),
    ).toHaveAttribute('href', 'https://link-to-console');
    expect(withinAppPanel.getByRole('link', {name: 'Modeler'})).toHaveAttribute(
      'href',
      'https://link-to-modeler',
    );
    expect(withinAppPanel.getByRole('link', {name: 'Operate'})).toHaveAttribute(
      'href',
      'https://link-to-operate',
    );
    expect(
      withinAppPanel.getByRole('link', {name: 'Tasklist'}),
    ).toHaveAttribute('href', '/');
    expect(
      withinAppPanel.getByRole('link', {name: 'Optimize'}),
    ).toHaveAttribute('href', 'https://link-to-optimize');
  });

  it('should not render links for CCSM', async () => {
    window.clientConfig = {
      ...window.clientConfig,
      isEnterprise: false,
      organizationId: null,
    };

    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res(ctx.data(mockGetCurrentUserWithC8Links.result.data));
      }),
    );

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(
      screen.queryByRole('button', {
        name: /app switcher/i,
      }),
    ).not.toBeInTheDocument();
  });
});
