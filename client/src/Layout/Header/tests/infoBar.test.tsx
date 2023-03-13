/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
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
  beforeAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    global.IS_REACT_ACT_ENVIRONMENT = true;
  });

  it('should render with correct links', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = jest.fn();
    window.open = mockOpenFn;

    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res(ctx.data(mockGetCurrentUser));
      }),
    );

    const {user} = render(<Header />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Demo User')).toBeInTheDocument();

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

    await user.click(screen.getByRole('button', {name: 'Camunda Academy'}));
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://academy.camunda.com/',
      '_blank',
    );

    await user.click(
      screen.getByRole('button', {name: 'Slack Community Channel'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/slack',
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

      const {user} = render(<Header />, {
        wrapper: Wrapper,
      });

      expect(await screen.findByText('Demo User')).toBeInTheDocument();

      await user.click(
        await screen.findByRole('button', {
          name: /info/i,
        }),
      );

      await user.click(
        screen.getByRole('button', {name: 'Feedback and Support'}),
      );
      expect(mockOpenFn).toHaveBeenLastCalledWith(link, '_blank');

      window.open = originalWindowOpen;
    },
  );
});
