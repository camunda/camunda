/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {
  mockGetCurrentUser,
  mockGetCurrentUserWithCustomSalesPlanType,
} from 'modules/queries/get-current-user';
import {User} from 'modules/types';
import {graphql} from 'msw';
import {Header} from '..';
import {Wrapper} from './mocks';

describe('Info bar', () => {
  it('should render with correct links', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = jest.fn();
    window.open = mockOpenFn;

    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res(ctx.data(mockGetCurrentUser));
      }),
    );

    render(<Header />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

    userEvent.click(
      await screen.findByRole('button', {
        name: /info/i,
      }),
    );

    userEvent.click(await screen.findByRole('button', {name: 'Documentation'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://docs.camunda.io/',
      '_blank',
    );

    userEvent.click(screen.getByRole('button', {name: 'Camunda Academy'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://academy.camunda.com/',
      '_blank',
    );

    userEvent.click(
      screen.getByRole('button', {name: 'Slack Community Channel'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda-slack-invite.herokuapp.com/',
      '_blank',
    );

    window.open = originalWindowOpen;
  });

  it.each<[User['salesPlanType'], string]>([
    ['free', 'https://forum.camunda.io/'],
    ['enterprise', 'https://jira.camunda.com/projects/SUPPORT/queues'],
    ['paid-cc', 'https://jira.camunda.com/projects/SUPPORT/queues'],
  ])(
    'should render correct links for feedback and support - %p',
    async (salesPlanType, link) => {
      nodeMockServer.use(
        graphql.query('GetCurrentUser', (_, res, ctx) => {
          return res(
            ctx.data(mockGetCurrentUserWithCustomSalesPlanType(salesPlanType)),
          );
        }),
      );

      const originalWindowOpen = window.open;
      const mockOpenFn = jest.fn();
      window.open = mockOpenFn;

      render(<Header />, {
        wrapper: Wrapper,
      });

      expect(await screen.findByText('Demo User')).toBeInTheDocument();

      userEvent.click(
        await screen.findByRole('button', {
          name: /info/i,
        }),
      );

      userEvent.click(
        screen.getByRole('button', {name: 'Feedback and Support'}),
      );
      expect(mockOpenFn).toHaveBeenLastCalledWith(link, '_blank');

      window.open = originalWindowOpen;
    },
  );
});
