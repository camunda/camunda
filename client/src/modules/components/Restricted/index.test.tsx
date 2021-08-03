/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Restricted} from './index';
import {render, screen} from '@testing-library/react';
import {graphql} from 'msw';
import {mockServer} from 'modules/mockServer';
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

  return <div>{data?.currentUser.firstname}</div>;
};
const Wrapper: React.FC = ({children}) => {
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
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser.result.data));
      }),
    );

    render(
      <Restricted scopes={['edit']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText('Demo')).toBeInTheDocument();
    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });

  it('should render content that user has permission for at least one scope', async () => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentRestrictedUser.result.data));
      }),
    );

    render(
      <Restricted scopes={['view', 'edit']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText('Demo')).toBeInTheDocument();
    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render content that user has permission for', async () => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUser.result.data));
      }),
    );

    render(
      <Restricted scopes={['edit']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText('Demo')).toBeInTheDocument();
    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should not render content when API returns an unknown role', async () => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(
          ctx.data(mockGetCurrentUserWithUnknownRole.result.data),
        );
      }),
    );

    render(
      <Restricted scopes={['view', 'edit']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText('Demo')).toBeInTheDocument();
    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });

  it('should not render content when API returns no roles', async () => {
    mockServer.use(
      graphql.query('GetCurrentUser', (_, res, ctx) => {
        return res.once(ctx.data(mockGetCurrentUserWithoutRole.result.data));
      }),
    );

    render(
      <Restricted scopes={['view', 'edit']}>
        <div>test content</div>
      </Restricted>,
      {wrapper: Wrapper},
    );

    expect(await screen.findByText('Demo')).toBeInTheDocument();
    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });
});
