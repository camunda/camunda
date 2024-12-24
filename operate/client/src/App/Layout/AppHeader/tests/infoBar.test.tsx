/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {AppHeader} from '../index';
import {authenticationStore} from 'modules/stores/authentication';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';
import {MeDto} from 'modules/api/v2/authentication/me';
import {Wrapper as BaseWrapper} from './mocks';
import {useEffect} from 'react';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return authenticationStore.reset;
  }, []);
  return <BaseWrapper>{children}</BaseWrapper>;
};

describe('Info bar', () => {
  it('should render with correct links', async () => {
    const originalWindowOpen = window.open;
    const mockOpenFn = jest.fn();
    window.open = mockOpenFn;

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
    await user.click(
      screen.getByRole('button', {name: 'Slack Community Channel'}),
    );
    expect(mockOpenFn).toHaveBeenLastCalledWith(
      'https://camunda.com/slack',
      '_blank',
    );

    window.open = originalWindowOpen;
  });

  it.each<[MeDto['salesPlanType'], string]>([
    ['free', 'https://forum.camunda.io/'],
    ['enterprise', 'https://jira.camunda.com/projects/SUPPORT/queues'],
    ['paid-cc', 'https://jira.camunda.com/projects/SUPPORT/queues'],
  ])(
    'should render correct links for feedback and support - %p',
    async (salesPlanType, link) => {
      mockMe().withSuccess(createUser({salesPlanType}));
      await authenticationStore.authenticate();

      const originalWindowOpen = window.open;
      const mockOpenFn = jest.fn();
      window.open = mockOpenFn;

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

      window.open = originalWindowOpen;
    },
  );
});
