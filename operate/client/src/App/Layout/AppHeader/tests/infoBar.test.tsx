/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {AppHeader} from '../index';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';
import {Wrapper as BaseWrapper} from './mocks';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <BaseWrapper>{children}</BaseWrapper>
    </QueryClientProvider>
  );
};

describe('Info bar', () => {
  it('should render with correct links', async () => {
    mockMe().withSuccess(createUser({authorizedApplications: ['operate']}));
    const mockOpenFn = vi.fn();
    vi.stubGlobal('open', mockOpenFn);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
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
    await user.click(screen.getByRole('button', {name: 'Community Forum'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://forum.camunda.io',
      '_blank',
    );
  });

  it('should not render feedback and support link for free plan', async () => {
    mockMe().withSuccess(
      createUser({salesPlanType: 'free', authorizedApplications: ['operate']}),
    );

    const mockOpenFn = vi.fn();
    vi.stubGlobal('open', mockOpenFn);

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await user.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
    );

    expect(
      screen.queryByRole('button', {name: 'Feedback and Support'}),
    ).not.toBeInTheDocument();
  });

  it.each([
    ['enterprise', 'https://jira.camunda.com/projects/SUPPORT/queues'],
    ['paid-cc', 'https://jira.camunda.com/projects/SUPPORT/queues'],
  ])(
    'should render correct links for feedback and support - %p',
    async (salesPlanType, link) => {
      mockMe().withSuccess(
        createUser({
          salesPlanType,
          authorizedApplications: ['operate'],
        }),
      );

      const mockOpenFn = vi.fn();
      vi.stubGlobal('open', mockOpenFn);

      const {user} = render(<AppHeader />, {
        wrapper: Wrapper,
      });

      await user.click(
        await screen.findByRole('button', {
          name: /info/i,
        }),
      );

      await user.click(
        screen.getByRole('button', {name: 'Feedback and Support'}),
      );
      expect(mockOpenFn).toHaveBeenLastCalledWith(link, '_blank');
    },
  );
});
