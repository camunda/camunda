/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import * as taskMocks from 'modules/mock-schema/mocks/task';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';
import {Aside} from './Aside';
import {getClientConfig} from 'modules/getClientConfig';
import {vi} from 'vitest';

vi.mock('modules/getClientConfig', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('modules/getClientConfig')>();
  return {
    getClientConfig: vi.fn().mockImplementation(actual.getClientConfig),
  };
});

const {getClientConfig: actualGetClientConfig} = await vi.importActual<
  typeof import('modules/getClientConfig')
>('modules/getClientConfig');
const mockGetClientConfig = vi.mocked(getClientConfig);

const UserName = () => {
  const {data: currentUser} = useCurrentUser();

  return <div>{currentUser?.displayName}</div>;
};

const getWrapper = (id: string = '0') => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <UserName />
      <MemoryRouter initialEntries={[`/${id}`]}>
        <Routes>
          <Route path="/:id" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('<Aside />', () => {
  beforeEach(() => {
    mockGetClientConfig.mockReturnValue(actualGetClientConfig());
    nodeMockServer.use(
      http.get('/v2/authentication/me', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
    );
  });

  it('should render completed task details', async () => {
    render(
      <Aside task={taskMocks.completedTask()} user={userMocks.currentUser} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('01 Jan 2024 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('Completion date')).toBeInTheDocument();
    expect(screen.getByText('01 Jan 2025 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('No candidates')).toBeInTheDocument();
  });

  it('should render unassigned task details', async () => {
    render(
      <Aside task={taskMocks.unassignedTask()} user={userMocks.currentUser} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('01 Jan 2024 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('accounting candidate')).toBeInTheDocument();
    expect(screen.getByText('jane candidate')).toBeInTheDocument();
  });

  it('should render tenant name', () => {
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      isMultiTenancyEnabled: true,
    });

    render(
      <Aside
        task={{
          ...taskMocks.unassignedTask(),
          tenantId: 'tenantA',
        }}
        user={userMocks.currentUserWithTenants}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('Tenant A')).toBeInTheDocument();
  });

  it('should hide tenant name if user only has access to one tenant', () => {
    mockGetClientConfig.mockReturnValue({
      ...actualGetClientConfig(),
      isMultiTenancyEnabled: true,
    });

    const currentUserWithSingleTenant = {
      ...userMocks.currentUserWithTenants,
      tenants: [userMocks.currentUserWithTenants.tenants[0]],
    };

    nodeMockServer.use(
      http.get('/v2/authentication/me', () => {
        return HttpResponse.json(currentUserWithSingleTenant);
      }),
    );

    render(
      <Aside
        task={{
          ...taskMocks.unassignedTask(),
          tenantId: 'tenantA',
        }}
        user={currentUserWithSingleTenant}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.queryByText('Tenant A')).not.toBeInTheDocument();
  });

  it.each([
    {priority: 20, label: 'Low'},
    {priority: 40, label: 'Medium'},
    {priority: 60, label: 'High'},
    {priority: 80, label: 'Critical'},
  ])('should render priority - $label', ({priority, label}) => {
    render(
      <Aside
        task={{...taskMocks.unassignedTask(), priority}}
        user={userMocks.currentUser}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText(label)).toBeInTheDocument();
  });
});
