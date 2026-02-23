/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {nodeMockServer} from 'common/testing/nodeMockServer';
import {http, HttpResponse} from 'msw';
import * as userMocks from 'common/mocks/current-user';
import {useCurrentUser} from 'common/api/useCurrentUser.query';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'common/testing/getMockQueryClient';
import {Aside} from './index';
import * as clientConfig from 'common/config/getClientConfig';

const completedTaskMock = {
  creationDate: '2024-01-01T00:00:00.000Z',
  completionDate: '2025-01-01T00:00:00.000Z',
  dueDate: '2025-02-15T00:00:00.000Z',
  followUpDate: null,
  priority: 50,
  candidateUsers: [],
  candidateGroups: [],
  tenantId: 'default',
};

const unassignedTaskMock = {
  creationDate: '2024-01-01T00:00:00.000Z',
  completionDate: null,
  dueDate: null,
  followUpDate: null,
  priority: 50,
  candidateUsers: ['jane candidate'],
  candidateGroups: ['accounting candidate'],
  tenantId: 'default',
};

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
    nodeMockServer.use(
      http.get('/v2/authentication/me', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
    );
  });

  it('should render completed task details', async () => {
    render(<Aside {...completedTaskMock} user={userMocks.currentUser} />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText('01 Jan 2024 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('Completion date')).toBeInTheDocument();
    expect(screen.getByText('01 Jan 2025 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('No candidates')).toBeInTheDocument();
  });

  it('should render unassigned task details', async () => {
    render(<Aside {...unassignedTaskMock} user={userMocks.currentUser} />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByText('01 Jan 2024 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('accounting candidate')).toBeInTheDocument();
    expect(screen.getByText('jane candidate')).toBeInTheDocument();
  });

  it('should render tenant name', () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      isMultiTenancyEnabled: true,
    });

    render(
      <Aside
        {...unassignedTaskMock}
        tenantId="tenantA"
        user={userMocks.currentUserWithTenants}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('Tenant A')).toBeInTheDocument();
  });

  it('should hide tenant name if user only has access to one tenant', () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
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
        {...unassignedTaskMock}
        tenantId="tenantA"
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
        {...unassignedTaskMock}
        priority={priority}
        user={userMocks.currentUser}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText(label)).toBeInTheDocument();
  });
});
