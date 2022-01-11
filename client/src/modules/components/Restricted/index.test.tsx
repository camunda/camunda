/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Restricted} from './index';
import {render, screen} from '@testing-library/react';
import {authenticationStore} from 'modules/stores/authentication';

describe('Restricted', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should not render content that user has no permission for', () => {
    authenticationStore.enableUserSession({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
    });

    render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>
    );

    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });

  it('should render content that user has permission for at least one scope', () => {
    authenticationStore.enableUserSession({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
    });

    render(
      <Restricted scopes={['read', 'write']}>
        <div>test content</div>
      </Restricted>
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render content that user has permission for', () => {
    authenticationStore.enableUserSession({
      displayName: 'demo',
      permissions: ['read', 'write'],
      canLogout: true,
    });

    render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render content by default (user has no permissions defined)', () => {
    authenticationStore.enableUserSession({
      displayName: 'demo',
      permissions: undefined,
      canLogout: true,
    });

    render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });
});
