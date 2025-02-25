/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {AppHeader} from '../index';
import {authenticationStore} from 'modules/stores/authentication';
import {mockMe} from 'modules/mocks/api/v2/me';
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

    mockMe().withSuccess(
      createUser({
        c8Links: {
          operate: 'https://link-to-operate',
          tasklist: 'https://link-to-tasklist',
          modeler: 'https://link-to-modeler',
          console: 'https://link-to-console',
          optimize: 'https://link-to-optimize',
        },
      }),
    );

    await authenticationStore.authenticate();
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

    const consoleLink = await withinAppPanel.findByRole('link', {
      name: 'Console',
    });
    expect(consoleLink).toHaveAttribute('href', 'https://link-to-console');
    expect(consoleLink).not.toHaveAttribute('target');

    const modelerLink = withinAppPanel.getByRole('link', {name: 'Modeler'});
    expect(modelerLink).toHaveAttribute('href', 'https://link-to-modeler');
    expect(modelerLink).not.toHaveAttribute('target');

    const tasklistLink = withinAppPanel.getByRole('link', {name: 'Tasklist'});
    expect(tasklistLink).toHaveAttribute('href', 'https://link-to-tasklist');
    expect(tasklistLink).not.toHaveAttribute('target');

    const operateLink = withinAppPanel.getByRole('link', {name: 'Operate'});
    expect(operateLink).toHaveAttribute('href', '/');
    expect(operateLink).not.toHaveAttribute('target');

    const optimizeLink = withinAppPanel.getByRole('link', {name: 'Optimize'});
    expect(optimizeLink).toHaveAttribute('href', 'https://link-to-optimize');
    expect(optimizeLink).not.toHaveAttribute('target');
  });

  it('should not render links for CCSM', async () => {
    window.clientConfig = {
      isEnterprise: false,
      organizationId: null,
    };

    mockMe().withSuccess(
      createUser({
        c8Links: {
          operate: 'https://link-to-operate',
          tasklist: 'https://link-to-tasklist',
          modeler: 'https://link-to-modeler',
          console: 'https://link-to-console',
          optimize: 'https://link-to-optimize',
        },
      }),
    );

    await authenticationStore.authenticate();
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
