/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
      <MemoryRouter initialEntries={[initialPath]}>
        {children}
        <button onClick={batchModificationStore.enable}>
          Enable batch modification mode
        </button>
      </MemoryRouter>
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
