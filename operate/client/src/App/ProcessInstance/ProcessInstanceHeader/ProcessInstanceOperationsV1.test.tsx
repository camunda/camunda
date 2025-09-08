/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {ProcessInstanceOperations} from './ProcessInstanceOperations';
import {createProcessInstance} from 'modules/testUtils';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter} from 'react-router-dom';
import {modificationsStore} from 'modules/stores/modifications';
import {notificationsStore} from 'modules/stores/notifications';
import {processInstancesStore} from 'modules/stores/processInstances';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

vi.mock('modules/feature-flags', async () => {
  const actual = await vi.importActual('modules/feature-flags');
  return {
    ...actual,
    IS_INCIDENT_RESOLUTION_V2: false,
  };
});

const getWrapper = () => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <MemoryRouter>
        <QueryClientProvider client={getMockQueryClient()}>
          {children}
        </QueryClientProvider>
      </MemoryRouter>
    );
  };
  return Wrapper;
};

describe('ProcessInstanceOperations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    modificationsStore.reset();
    processInstancesStore.reset();
    mockFetchCallHierarchy().withSuccess([]);
  });

  it('should show notification on legacy resolve incident error', async () => {
    mockApplyOperation().withServerError();
    const instanceWithIncident = createProcessInstance({
      state: 'ACTIVE',
      hasIncident: true,
    });

    const {user} = render(
      <ProcessInstanceOperations processInstance={instanceWithIncident} />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByTitle(/retry instance/i));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Operation could not be created',
        isDismissable: true,
      }),
    );
  });
});
