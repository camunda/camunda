/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Restricted} from './index';
import {render, screen} from '@testing-library/react';
import {authenticationStore} from 'modules/stores/authentication';

describe('Restricted', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should not render content that user has no permission for', () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
    });

    render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>
    );

    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });

  it('should render content that user has permission for at least one scope', () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
    });

    render(
      <Restricted scopes={['read', 'write']}>
        <div>test content</div>
      </Restricted>
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render content that user has permission for', () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read', 'write'],
      canLogout: true,
      userId: 'demo',
    });

    render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render content by default (user has no permissions defined)', () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: undefined,
      canLogout: true,
      userId: 'demo',
    });

    render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });
});
