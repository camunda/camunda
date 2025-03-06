/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {http, HttpResponse} from 'msw';
import {Header} from '..';
import {getWrapper} from './mocks';
import * as userMocks from 'common/mocks/current-user';
import * as licenseMocks from 'common/mocks/license';

describe('Info bar', () => {
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
  });

  it('should render links without a plan', async () => {
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

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
    );

    await user.click(screen.getByRole('button', {name: 'Documentation'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://docs.camunda.io/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
    );

    await user.click(screen.getByRole('button', {name: 'Camunda Academy'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://academy.camunda.com/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
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
            ...userMocks.currentUser,
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

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
    );

    await user.click(screen.getByRole('button', {name: 'Documentation'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://docs.camunda.io/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
    );

    await user.click(screen.getByRole('button', {name: 'Camunda Academy'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://academy.camunda.com/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
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
            ...userMocks.currentUser,
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

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
    );

    await user.click(screen.getByRole('button', {name: 'Documentation'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://docs.camunda.io/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
    );

    await user.click(screen.getByRole('button', {name: 'Camunda Academy'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://academy.camunda.com/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
    );

    await user.click(
      screen.getByRole('button', {name: 'Feedback and Support'}),
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
            ...userMocks.currentUser,
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

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
    );

    await user.click(screen.getByRole('button', {name: 'Documentation'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://docs.camunda.io/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
    );

    await user.click(screen.getByRole('button', {name: 'Camunda Academy'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://academy.camunda.com/',
      '_blank',
    );

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
    );

    await user.click(
      screen.getByRole('button', {name: 'Feedback and Support'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://jira.camunda.com/projects/SUPPORT/queues',
      '_blank',
    );

    window.open = originalWindowOpen;
  });
});
