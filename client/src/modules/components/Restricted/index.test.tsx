/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Restricted} from './index';
import {render, screen} from '@testing-library/react';
import {graphql} from 'msw';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {
  GET_CURRENT_USER,
  GetCurrentUser,
  mockGetCurrentUser,
  mockGetCurrentRestrictedUser,
  mockGetCurrentUserWithUnknownRole,
  mockGetCurrentUserWithoutRole,
} from 'modules/queries/get-current-user';
import {MemoryRouter} from 'react-router-dom';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {ApolloProvider, useQuery} from '@apollo/client';
import {client} from 'modules/apollo-client';

const UserName = () => {
  const {data} = useQuery<GetCurrentUser>(GET_CURRENT_USER);

  return <div>{data?.currentUser.displayName}</div>;
};
type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ApolloProvider client={client}>
      <MemoryRouter initialEntries={['/']}>
        <MockThemeProvider>
          <>
            <UserName />
            {children}
          </>
        </MockThemeProvider>
      </MemoryRouter>
    </ApolloProvider>
  );
};

describe('Restricted', () => {
  it('should not render content that user has no permission for', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser));
      }),
    );

    render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });

  it('should render content that user has permission for at least one scope', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser));
      }),
    );

    render(
      <Restricted scopes={['read', 'write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render content that user has permission for', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser));
      }),
    );

    render(
      <Restricted scopes={['write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should not render content when API returns an unknown permission', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUserWithUnknownRole));
      }),
    );

    render(
      <Restricted scopes={['read', 'write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });

  it('should not render content when API returns no permissions', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUserWithoutRole));
      }),
    );

    render(
      <Restricted scopes={['read', 'write']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });

  it('should render a fallback', async () => {
    nodeMockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser));
      }),
    );

    const mockFallback = 'mock fallback';

    render(
      <Restricted scopes={['write']} fallback={mockFallback}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText('Demo User')).toBeInTheDocument();
    expect(screen.getByText(mockFallback)).toBeInTheDocument();
    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });
});
