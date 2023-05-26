/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Restricted} from './index';
import {render, screen} from 'modules/testing-library';
import {rest} from 'msw';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {MemoryRouter} from 'react-router-dom';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {ReactQueryProvider} from 'modules/ReactQueryProvider';

const UserName = () => {
  const {data: currentUser} = useCurrentUser();

  return <div>{currentUser?.displayName}</div>;
};
type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ReactQueryProvider>
      <MemoryRouter initialEntries={['/']}>
        <MockThemeProvider>
          <>
            <UserName />
            {children}
          </>
        </MockThemeProvider>
      </MemoryRouter>
    </ReactQueryProvider>
  );
};

describe('Restricted', () => {
  it('should not render content that user has no permission for', async () => {
    nodeMockServer.use(
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentRestrictedUser));
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
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentRestrictedUser));
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
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUser));
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
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUserWithUnknownRole));
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
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentUserWithoutRole));
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
      rest.get('/v1/internal/users/current', (_, res, ctx) => {
        return res.once(ctx.json(userMocks.currentRestrictedUser));
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
