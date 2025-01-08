/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Restricted} from './index';
import {render, screen} from 'modules/testing-library';
import {
  createMockAuthenticationMeHandler,
  nodeMockServer,
} from 'modules/mockServer/nodeMockServer';
import {MemoryRouter} from 'react-router-dom';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';

const UserName = () => {
  const {data: currentUser} = useCurrentUser();

  return <div>{currentUser?.displayName}</div>;
};

const getWrapper = () => {
  const mockClient = getMockQueryClient();

  type Props = {
    children?: React.ReactNode;
  };

  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <QueryClientProvider client={mockClient}>
        <MemoryRouter initialEntries={['/']}>
          <UserName />
          {children}
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
};

describe('Restricted', () => {
  it('should not render content that user has no permission for', async () => {
    nodeMockServer.use(
      createMockAuthenticationMeHandler(userMocks.currentRestrictedUser),
    );

    render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: getWrapper()},
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });

  it('should render content that user has permission for at least one scope', async () => {
    nodeMockServer.use(
      createMockAuthenticationMeHandler(userMocks.currentRestrictedUser),
    );

    render(
      <Restricted scopes={['read', 'write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: getWrapper()},
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render content that user has permission for', async () => {
    render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: getWrapper()},
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should not render content when API returns an unknown permission', async () => {
    nodeMockServer.use(
      createMockAuthenticationMeHandler(userMocks.currentUserWithUnknownRole),
    );

    render(
      <Restricted scopes={['read', 'write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: getWrapper()},
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });

  it('should not render content when API returns no permissions', async () => {
    nodeMockServer.use(
      createMockAuthenticationMeHandler(userMocks.currentUserWithoutRole),
    );

    render(
      <Restricted scopes={['read', 'write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: getWrapper()},
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });

  it('should render a fallback', async () => {
    nodeMockServer.use(
      createMockAuthenticationMeHandler(userMocks.currentRestrictedUser),
    );

    const mockFallback = 'mock fallback';

    render(
      <Restricted scopes={['write']} fallback={mockFallback}>
        <div>test content</div>
      </Restricted>,
      {wrapper: getWrapper()},
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.getByText(mockFallback)).toBeInTheDocument();
    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });
});
