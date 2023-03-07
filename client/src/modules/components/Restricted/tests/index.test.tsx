/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Restricted} from '../index';
import {render, screen} from 'modules/testing-library';
import {authenticationStore} from 'modules/stores/authentication';
import {useEffect} from 'react';
import {MemoryRouter} from 'react-router-dom';

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  useEffect(() => {
    return () => {
      authenticationStore.reset();
    };
  }, []);

  return <MemoryRouter>{children}</MemoryRouter>;
};

describe('Restricted', () => {
  it('should not render content that user has no permission for', () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper}
    );

    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });

  it('should render content that user has permission for at least one scope', () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(
      <Restricted scopes={['read', 'write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper}
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render content that user has permission for', () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read', 'write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper}
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render content by default (user has no permissions defined)', () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: undefined,
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper}
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render fallback', () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });
    const mockFallback = 'i am a fallback';

    render(
      <Restricted scopes={['write']} fallback={mockFallback}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper}
    );

    expect(screen.getByText(mockFallback)).toBeInTheDocument();
  });
});
