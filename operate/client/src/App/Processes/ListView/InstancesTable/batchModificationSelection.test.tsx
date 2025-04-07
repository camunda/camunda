/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {InstancesTable} from '.';
import {MemoryRouter} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {batchModificationStore} from 'modules/stores/batchModification';
import {useEffect} from 'react';
import {processStatisticsBatchModificationStore} from 'modules/stores/processStatistics/processStatistics.batchModification';

import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockProcessInstances, mockProcessStatistics} from 'modules/testUtils';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

jest.mock('modules/hooks/useCallbackPrompt', () => {
  return {
    useCallbackPrompt: () => ({
      shouldInterrupt: false,
      confirmNavigation: jest.fn(),
      cancelNavigation: jest.fn(),
    }),
  };
});

function getWrapper(initialPath: string = Paths.processes()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        batchModificationStore.reset();
        processInstancesSelectionStore.reset();
        processStatisticsBatchModificationStore.reset();
      };
    });

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

describe('<InstancesTable /> - batch modification selection', () => {
  beforeEach(async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    await processInstancesStore.fetchProcessInstancesFromFilters();
  });

  it('should fetch statistics when all instances are selected', async () => {
    const {user} = render(<InstancesTable />, {wrapper: getWrapper()});
    const fetchProcessStatisticsSpy = jest.spyOn(
      processStatisticsBatchModificationStore,
      'fetchProcessStatistics',
    );

    await user.click(
      screen.getByRole('button', {name: /enable batch modification mode/i}),
    );

    mockFetchProcessInstancesStatistics().withSuccess([]);
    await user.click(
      await screen.findByRole('checkbox', {name: /select all rows/i}),
    );

    expect(fetchProcessStatisticsSpy).toHaveBeenCalledTimes(1);
    expect(fetchProcessStatisticsSpy).toHaveBeenCalledWith({
      canceled: false,
      completed: false,
      excludeIds: [],
      finished: false,
      ids: [],
    });
  });

  it('should fetch statistics when all instances are selected (with id filter)', async () => {
    const {user} = render(<InstancesTable />, {
      wrapper: getWrapper(
        `/?ids=${mockProcessInstances.processInstances[0]?.id}`,
      ),
    });
    const fetchProcessStatisticsSpy = jest.spyOn(
      processStatisticsBatchModificationStore,
      'fetchProcessStatistics',
    );

    await user.click(
      screen.getByRole('button', {name: /enable batch modification mode/i}),
    );

    mockFetchProcessInstancesStatistics().withSuccess([]);
    await user.click(
      await screen.findByRole('checkbox', {name: /select all rows/i}),
    );

    expect(fetchProcessStatisticsSpy).toHaveBeenCalledTimes(1);
    expect(fetchProcessStatisticsSpy).toHaveBeenCalledWith({
      canceled: false,
      completed: false,
      excludeIds: [],
      finished: false,
      ids: [mockProcessInstances.processInstances[0]?.id],
    });
  });

  it('should fetch statistics when two instances are selected', async () => {
    const {user} = render(<InstancesTable />, {wrapper: getWrapper()});
    const fetchProcessStatisticsSpy = jest.spyOn(
      processStatisticsBatchModificationStore,
      'fetchProcessStatistics',
    );

    mockFetchProcessInstancesStatistics().withSuccess([]);
    await user.click(
      screen.getByRole('button', {name: /enable batch modification mode/i}),
    );

    // wait for process instances to be fetched
    expect(
      await screen.findByText(/process instances .* results/i),
    ).toBeInTheDocument();

    const [firstInstanceCheckbox, secondInstanceCheckbox] = screen.getAllByRole(
      'checkbox',
      {name: /select row/i},
    );

    mockFetchProcessInstancesStatistics().withSuccess([]);
    await user.click(firstInstanceCheckbox!);

    expect(fetchProcessStatisticsSpy).toHaveBeenNthCalledWith(1, {
      canceled: false,
      completed: false,
      excludeIds: [],
      finished: false,
      ids: [mockProcessInstances.processInstances[0]?.id],
    });

    mockFetchProcessInstancesStatistics().withSuccess([]);
    await user.click(secondInstanceCheckbox!);
    expect(fetchProcessStatisticsSpy).toHaveBeenNthCalledWith(2, {
      canceled: false,
      completed: false,
      excludeIds: [],
      finished: false,
      ids: [
        mockProcessInstances.processInstances[0]?.id,
        mockProcessInstances.processInstances[1]?.id,
      ],
    });
    expect(fetchProcessStatisticsSpy).toHaveBeenCalledTimes(2);
  });

  it('should fetch statistics when two instances are excluded', async () => {
    const {user} = render(<InstancesTable />, {wrapper: getWrapper()});

    const fetchProcessStatisticsSpy = jest.spyOn(
      processStatisticsBatchModificationStore,
      'fetchProcessStatistics',
    );

    mockFetchProcessInstancesStatistics().withSuccess([]);
    await user.click(
      screen.getByRole('button', {name: /enable batch modification mode/i}),
    );

    // wait for process instances to be fetched
    expect(
      await screen.findByText(/process instances .* results/i),
    ).toBeInTheDocument();

    const [firstInstanceCheckbox, secondInstanceCheckbox] = screen.getAllByRole(
      'checkbox',
      {name: /select row/i},
    );

    mockFetchProcessInstancesStatistics().withSuccess([]);
    await user.click(
      await screen.findByRole('checkbox', {name: /select all rows/i}),
    );
    expect(fetchProcessStatisticsSpy).toHaveBeenNthCalledWith(1, {
      canceled: false,
      completed: false,
      excludeIds: [],
      finished: false,
      ids: [],
    });

    mockFetchProcessInstancesStatistics().withSuccess([]);
    await user.click(firstInstanceCheckbox!);

    expect(fetchProcessStatisticsSpy).toHaveBeenNthCalledWith(2, {
      canceled: false,
      completed: false,
      excludeIds: [mockProcessInstances.processInstances[0]?.id],
      finished: false,
      ids: [],
    });

    mockFetchProcessInstancesStatistics().withSuccess([]);
    await user.click(secondInstanceCheckbox!);
    expect(fetchProcessStatisticsSpy).toHaveBeenNthCalledWith(3, {
      canceled: false,
      completed: false,
      excludeIds: [
        mockProcessInstances.processInstances[0]?.id,
        mockProcessInstances.processInstances[1]?.id,
      ],
      finished: false,
      ids: [],
    });
    expect(fetchProcessStatisticsSpy).toHaveBeenCalledTimes(3);
  });

  it('should fetch statistics when one instance is excluded (with id filter)', async () => {
    const [firstInstance, secondInstance] =
      mockProcessInstances.processInstances;

    const {user} = render(<InstancesTable />, {
      wrapper: getWrapper(`/?ids=${firstInstance?.id},${secondInstance?.id}`),
    });

    const fetchProcessStatisticsSpy = jest.spyOn(
      processStatisticsBatchModificationStore,
      'fetchProcessStatistics',
    );

    mockFetchProcessInstancesStatistics().withSuccess([]);
    await user.click(
      screen.getByRole('button', {name: /enable batch modification mode/i}),
    );

    // wait for process instances to be fetched
    expect(
      await screen.findByText(/process instances .* results/i),
    ).toBeInTheDocument();

    const [firstInstanceCheckbox] = screen.getAllByRole('checkbox', {
      name: /select row/i,
    });

    mockFetchProcessInstancesStatistics().withSuccess([]);
    await user.click(
      await screen.findByRole('checkbox', {name: /select all rows/i}),
    );
    expect(fetchProcessStatisticsSpy).toHaveBeenNthCalledWith(1, {
      canceled: false,
      completed: false,
      excludeIds: [],
      finished: false,
      ids: [firstInstance?.id, secondInstance?.id],
    });

    mockFetchProcessInstancesStatistics().withSuccess([]);
    await user.click(firstInstanceCheckbox!);

    expect(fetchProcessStatisticsSpy).toHaveBeenNthCalledWith(2, {
      canceled: false,
      completed: false,
      excludeIds: [firstInstance?.id],
      finished: false,
      ids: [firstInstance?.id, secondInstance?.id],
    });
  });

  it('should reset statistics when no instance is selected', async () => {
    const {user} = render(<InstancesTable />, {wrapper: getWrapper()});
    const fetchProcessStatisticsSpy = jest.spyOn(
      processStatisticsBatchModificationStore,
      'fetchProcessStatistics',
    );

    mockFetchProcessInstancesStatistics().withSuccess([]);
    await user.click(
      screen.getByRole('button', {name: /enable batch modification mode/i}),
    );

    // wait for process instances to be fetched
    expect(
      await screen.findByText(/process instances .* results/i),
    ).toBeInTheDocument();

    const [firstInstanceCheckbox] = screen.getAllByRole('checkbox', {
      name: /select row/i,
    });

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    // check first instance checkbox
    await user.click(firstInstanceCheckbox!);

    expect(fetchProcessStatisticsSpy).toHaveBeenNthCalledWith(1, {
      canceled: false,
      completed: false,
      excludeIds: [],
      finished: false,
      ids: [mockProcessInstances.processInstances[0]?.id],
    });
    await waitFor(() =>
      expect(processStatisticsBatchModificationStore.state.statistics).toEqual(
        mockProcessStatistics,
      ),
    );

    // uncheck first instance checkbox
    await user.click(firstInstanceCheckbox!);

    expect(fetchProcessStatisticsSpy).toHaveBeenCalledTimes(1);
    expect(processStatisticsBatchModificationStore.state.statistics).toEqual(
      [],
    );
  });
});
