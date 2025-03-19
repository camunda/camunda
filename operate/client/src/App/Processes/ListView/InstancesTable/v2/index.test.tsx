/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {InstancesTable} from '.';
import {unstable_HistoryRouter as HistoryRouter} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {batchModificationStore} from 'modules/stores/batchModification';
import {useEffect} from 'react';
import {createMemoryHistory} from 'history';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processesStore} from 'modules/stores/processes/processes.list';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {groupedProcessesMock, mockProcessInstances} from 'modules/testUtils';

jest.mock('modules/utils/bpmn');

function getWrapper(initialPath: string = Paths.processes()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processInstancesStore.reset();
        processInstancesSelectionStore.reset();
        processesStore.reset();
        batchModificationStore.reset();
      };
    }, []);

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <HistoryRouter
          history={createMemoryHistory({
            initialEntries: [initialPath],
          })}
        >
          {children}
          <button onClick={batchModificationStore.enable}>
            Enable batch modification mode
          </button>
        </HistoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('<InstancesTable />', () => {
  const locationSpy = jest.spyOn(window, 'location', 'get');

  beforeEach(() => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessXML().withSuccess('');

    processesStore.fetchProcesses();
  });

  afterEach(() => {
    locationSpy.mockClear();
  });

  it.each(['all', undefined])(
    'should show tenant column when multi tenancy is enabled and tenant filter is %p',
    async (tenant) => {
      window.clientConfig = {
        multiTenancyEnabled: true,
      };

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

      window.clientConfig = undefined;
    },
  );

  it('should hide tenant column when multi tenancy is enabled and tenant filter is a specific tenant', async () => {
    window.clientConfig = {
      multiTenancyEnabled: true,
    };

    render(<InstancesTable />, {
      wrapper: getWrapper(
        `${Paths.processes()}?${new URLSearchParams({tenant: 'tenant-a'})}`,
      ),
    });

    expect(
      screen.queryByRole('columnheader', {name: 'Tenant'}),
    ).not.toBeInTheDocument();

    window.clientConfig = undefined;
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
