/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {AppHeader} from '../index';
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser} from 'modules/testUtils';
import {Wrapper as BaseWrapper} from './mocks';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {getClientConfig} from 'modules/utils/getClientConfig';

vi.mock('modules/utils/getClientConfig', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('modules/utils/getClientConfig')>();
  return {
    getClientConfig: vi.fn().mockImplementation(actual.getClientConfig),
  };
});

const {getClientConfig: actualGetClientConfig} = await vi.importActual<
  typeof import('modules/utils/getClientConfig')
>('modules/utils/getClientConfig');

const mockGetClientConfig = vi.mocked(getClientConfig);

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <BaseWrapper>{children}</BaseWrapper>
    </QueryClientProvider>
  );
};

describe('App switcher', () => {
  it('should not render links for CCSM', async () => {
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      multiTenancyEnabled: true,
      organizationId: null,
    });

    mockMe().withSuccess(
      createUser({
        c8Links: [
          {name: 'operate', link: 'https://link-to-operate'},
          {name: 'tasklist', link: 'https://link-to-tasklist'},
          {name: 'modeler', link: 'https://link-to-modeler'},
          {name: 'console', link: 'https://link-to-console'},
          {name: 'optimize', link: 'https://link-to-optimize'},
        ],
        authorizedComponents: ['operate'],
      }),
    );

    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await user.click(
      await screen.findByRole('button', {
        name: /camunda components/i,
      }),
    );

    const withinAppPanel = within(
      screen.getByRole('navigation', {
        name: /app panel/i,
      }),
    );

    expect(
      withinAppPanel.queryByRole('link', {name: 'Console'}),
    ).not.toBeInTheDocument();

    expect(
      withinAppPanel.queryByRole('link', {name: 'Modeler'}),
    ).not.toBeInTheDocument();

    expect(
      withinAppPanel.queryByRole('link', {name: 'Tasklist'}),
    ).not.toBeInTheDocument();

    expect(
      withinAppPanel.queryByRole('link', {name: 'Optimize'}),
    ).not.toBeInTheDocument();
  });
});
