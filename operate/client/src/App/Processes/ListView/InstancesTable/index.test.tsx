/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {InstancesTable} from './index';
import {MemoryRouter} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {batchModificationStore} from 'modules/stores/batchModification';
import {useEffect} from 'react';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import * as clientConfig from 'modules/utils/getClientConfig';

vi.mock('modules/utils/bpmn');
vi.mock('modules/hooks/useCallbackPrompt', () => {
  return {
    useCallbackPrompt: () => ({
      shouldInterrupt: false,
      confirmNavigation: vi.fn(),
      cancelNavigation: vi.fn(),
    }),
  };
});

const mockProcessInstances: ProcessInstance[] = [
  {
    processInstanceKey: '123',
    processDefinitionKey: 'process-1',
    processDefinitionId: 'process-id',
    processDefinitionName: 'Test Process',
    processDefinitionVersion: 1,
    processDefinitionVersionTag: 'v1.0',
    startDate: '2024-01-01T00:00:00.000Z',
    endDate: undefined,
    state: 'ACTIVE',
    hasIncident: false,
    tenantId: 'tenant-a',
    parentProcessInstanceKey: undefined,
    parentElementInstanceKey: undefined,
  },
];

function getWrapper(initialPath: string = Paths.processes()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processInstancesSelectionStore.reset();

        batchModificationStore.reset();
      };
    }, []);

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[initialPath]}>
          {children}
          <button onClick={batchModificationStore.enable}>
            Enable batch modification mode
          </button>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('<InstancesTable />', () => {
  beforeEach(() => {
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
  });

  it.each(['all', undefined])(
    'should show tenant column when multi tenancy is enabled and tenant filter is %p',
    async (tenant) => {
      vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
        ...clientConfig.getClientConfig(),
        multiTenancyEnabled: true,
      });
      render(
        <InstancesTable
          state="content"
          processInstances={mockProcessInstances}
          totalProcessInstancesCount={mockProcessInstances.length}
        />,
        {
          wrapper: getWrapper(
            `${Paths.processes()}?${new URLSearchParams(
              tenant === undefined ? undefined : {tenant},
            )}`,
          ),
        },
      );

      expect(
        screen.getByRole('columnheader', {name: /Tenant/i}),
      ).toBeInTheDocument();
    },
  );

  it('should hide tenant column when multi tenancy is enabled and tenant filter is a specific tenant', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      multiTenancyEnabled: true,
    });

    render(
      <InstancesTable
        state="content"
        processInstances={mockProcessInstances}
        totalProcessInstancesCount={mockProcessInstances.length}
      />,
      {
        wrapper: getWrapper(
          `${Paths.processes()}?${new URLSearchParams({tenant: 'tenant-a'})}`,
        ),
      },
    );

    expect(
      screen.queryByRole('columnheader', {name: 'Tenant'}),
    ).not.toBeInTheDocument();
  });

  it('should hide tenant column when multi tenancy is disabled', async () => {
    render(
      <InstancesTable
        state="content"
        processInstances={mockProcessInstances}
        totalProcessInstancesCount={mockProcessInstances.length}
      />,
      {
        wrapper: getWrapper(
          `${Paths.processes()}?${new URLSearchParams({tenant: 'all'})}`,
        ),
      },
    );

    expect(
      screen.queryByRole('columnheader', {name: 'Tenant'}),
    ).not.toBeInTheDocument();
  });

  it('should render batch modification footer', async () => {
    const {user} = render(
      <InstancesTable
        state="content"
        processInstances={mockProcessInstances}
        totalProcessInstancesCount={mockProcessInstances.length}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(
      screen.getByRole('button', {name: /enable batch modification mode/i}),
    );

    expect(batchModificationStore.state.isEnabled).toBe(true);
    expect(
      screen.getByRole('button', {name: /apply modification/i}),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /exit/i}));

    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {name: /exit/i}),
    );

    expect(batchModificationStore.state.isEnabled).toBe(false);
    expect(
      screen.queryByRole('button', {name: /apply modification/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /exit/i}),
    ).not.toBeInTheDocument();
  });

  it('should display empty state message when there are no process instances', async () => {
    render(
      <InstancesTable
        state="empty"
        processInstances={[]}
        totalProcessInstancesCount={0}
      />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.getByText('There are no Instances matching this filter set'),
    ).toBeInTheDocument();
  });
});
