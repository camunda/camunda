/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {vi} from 'vitest';
vi.mock('modules/featureFlags', () => ({IS_NAV_V2_ENABLED: true}));

import {render, screen} from 'modules/testing/testing-library';
import {nodeMockServer} from 'modules/testing/nodeMockServer';
import {http, HttpResponse} from 'msw';
import {Header} from '..';
import {getWrapper} from './mocks';
import {currentUser, saasLicense} from '@camunda/c8-mocks';

describe('Info bar (V2)', () => {
  beforeEach(() => {
    nodeMockServer.use(
      http.get(
        '/v2/license',
        () => {
          return HttpResponse.json(saasLicense);
        },
        {
          once: true,
        },
      ),
    );
  });

  it('should render links without a plan', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = vi.fn();
    window.open = mockOpenFn;

    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json(currentUser);
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
        name: /info/i,
      }),
    );

    await user.click(
      await screen.findByRole('button', {name: 'Documentation'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://docs.camunda.io/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {name: 'Camunda Academy'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://academy.camunda.com/',
      '_blank',
    );

    expect(
      screen.queryByRole('button', {name: 'Feedback and Support'}),
    ).not.toBeInTheDocument();

    window.open = originalWindowOpen;
  });

  it('should render links for free plan', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = vi.fn();
    window.open = mockOpenFn;

    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json({
            ...currentUser,
            salesPlanType: 'free',
          });
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
        name: /info/i,
      }),
    );

    await user.click(
      await screen.findByRole('button', {name: 'Documentation'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://docs.camunda.io/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {name: 'Camunda Academy'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://academy.camunda.com/',
      '_blank',
    );

    expect(
      screen.queryByRole('button', {name: 'Feedback and Support'}),
    ).not.toBeInTheDocument();

    window.open = originalWindowOpen;
  });

  it('should render links for paid plan', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = vi.fn();
    window.open = mockOpenFn;

    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json({
            ...currentUser,
            salesPlanType: 'paid-cc',
          });
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
        name: /info/i,
      }),
    );

    await user.click(
      await screen.findByRole('button', {name: 'Documentation'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://docs.camunda.io/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {name: 'Camunda Academy'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://academy.camunda.com/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {name: 'Feedback and Support'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://jira.camunda.com/projects/SUPPORT/queues',
      '_blank',
    );

    window.open = originalWindowOpen;
  });

  it('should render links for enterprise plan', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = vi.fn();
    window.open = mockOpenFn;

    nodeMockServer.use(
      http.get(
        '/v2/authentication/me',
        () => {
          return HttpResponse.json({
            ...currentUser,
            salesPlanType: 'enterprise',
          });
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
        name: /info/i,
      }),
    );

    await user.click(
      await screen.findByRole('button', {name: 'Documentation'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://docs.camunda.io/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {name: 'Camunda Academy'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://academy.camunda.com/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {name: 'Feedback and Support'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://jira.camunda.com/projects/SUPPORT/queues',
      '_blank',
    );

    window.open = originalWindowOpen;
  });
});
