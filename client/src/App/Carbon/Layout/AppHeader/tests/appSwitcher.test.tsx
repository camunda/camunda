/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, within} from 'modules/testing-library';
import {AppHeader} from '../index';
import {authenticationStore} from 'modules/stores/authentication';
import {mockGetUser} from 'modules/mocks/api/getUser';
import {createUser} from 'modules/testUtils';
import {Wrapper as BaseWrapper} from './mocks';
import {useEffect} from 'react';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return authenticationStore.reset;
  }, []);
  return <BaseWrapper>{children}</BaseWrapper>;
};

describe('App switcher', () => {
  afterEach(() => {
    window.clientConfig = undefined;
  });

  it('should render with correct links', async () => {
    window.clientConfig = {
      isEnterprise: false,
      organizationId: 'some-organization-id',
    };

    mockGetUser().withSuccess(
      createUser({
        c8Links: {
          operate: 'https://link-to-operate',
          tasklist: 'https://link-to-tasklist',
          modeler: 'https://link-to-modeler',
          console: 'https://link-to-console',
          optimize: 'https://link-to-optimize',
        },
      })
    );

    await authenticationStore.authenticate();
    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await user.click(
      await screen.findByRole('button', {
        name: /app switcher/i,
      })
    );

    const withinAppPanel = within(
      screen.getByRole('navigation', {
        name: /app panel/i,
      })
    );

    expect(
      await withinAppPanel.findByRole('link', {name: 'Console'})
    ).toHaveAttribute('href', 'https://link-to-console');
    expect(withinAppPanel.getByRole('link', {name: 'Modeler'})).toHaveAttribute(
      'href',
      'https://link-to-modeler'
    );
    expect(
      withinAppPanel.getByRole('link', {name: 'Tasklist'})
    ).toHaveAttribute('href', 'https://link-to-tasklist');
    expect(withinAppPanel.getByRole('link', {name: 'Operate'})).toHaveAttribute(
      'href',
      '/carbon'
    );
    expect(
      withinAppPanel.getByRole('link', {name: 'Optimize'})
    ).toHaveAttribute('href', 'https://link-to-optimize');
  });

  it('should not render links for CCSM', async () => {
    window.clientConfig = {
      isEnterprise: false,
      organizationId: null,
    };

    mockGetUser().withSuccess(
      createUser({
        c8Links: {
          operate: 'https://link-to-operate',
          tasklist: 'https://link-to-tasklist',
          modeler: 'https://link-to-modeler',
          console: 'https://link-to-console',
          optimize: 'https://link-to-optimize',
        },
      })
    );

    await authenticationStore.authenticate();
    const {user} = render(<AppHeader />, {
      wrapper: Wrapper,
    });

    await user.click(
      await screen.findByRole('button', {
        name: /app switcher/i,
      })
    );

    const withinAppPanel = within(
      screen.getByRole('navigation', {
        name: /app panel/i,
      })
    );

    expect(
      withinAppPanel.queryByRole('link', {name: 'Console'})
    ).not.toBeInTheDocument();

    expect(
      withinAppPanel.queryByRole('link', {name: 'Modeler'})
    ).not.toBeInTheDocument();

    expect(
      withinAppPanel.queryByRole('link', {name: 'Tasklist'})
    ).not.toBeInTheDocument();

    expect(
      withinAppPanel.queryByRole('link', {name: 'Optimize'})
    ).not.toBeInTheDocument();
  });
});
