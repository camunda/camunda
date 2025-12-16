/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {InstancesTable} from '.';
import {MemoryRouter} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {batchModificationStore} from 'modules/stores/batchModification';
import {useEffect} from 'react';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockProcessInstances} from 'modules/testUtils';

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

function getWrapper(initialPath: string = Paths.processes()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processInstancesStore.reset();
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
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
  });

  it.each(['all', undefined])(
    'should show tenant column when multi tenancy is enabled and tenant filter is %p',
    async (tenant) => {
      vi.stubGlobal('clientConfig', {
        multiTenancyEnabled: true,
      });

      render(<InstancesTable />, {
        wrapper: getWrapper(
          `${Paths.processes()}?${new URLSearchParams(
            tenant === undefined ? undefined : {tenant},
          )}`,
        ),
      });

      expect(
        screen.getByRole('columnheader', {name: 'Tenant'}),
      ).toBeInTheDocument();
    },
  );

  it('should hide tenant column when multi tenancy is enabled and tenant filter is a specific tenant', async () => {
    vi.stubGlobal('clientConfig', {
      multiTenancyEnabled: true,
    });

    render(<InstancesTable />, {
      wrapper: getWrapper(
        `${Paths.processes()}?${new URLSearchParams({tenant: 'tenant-a'})}`,
      ),
    });

    expect(
      screen.queryByRole('columnheader', {name: 'Tenant'}),
    ).not.toBeInTheDocument();
  });

  it('should hide tenant column when multi tenancy is disabled', async () => {
    render(<InstancesTable />, {
      wrapper: getWrapper(
        `${Paths.processes()}?${new URLSearchParams({tenant: 'all'})}`,
      ),
    });

    expect(
      screen.queryByRole('columnheader', {name: 'Tenant'}),
    ).not.toBeInTheDocument();
  });

  it('should render batch modification footer', async () => {
    const {user} = render(<InstancesTable />, {wrapper: getWrapper()});

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
    processInstancesStore.state.processInstances = [];
    processInstancesStore.state.status = 'fetched';

    render(<InstancesTable />, {wrapper: getWrapper()});

    expect(
      screen.getByText('There are no Instances matching this filter set'),
    ).toBeInTheDocument();
  });
});
