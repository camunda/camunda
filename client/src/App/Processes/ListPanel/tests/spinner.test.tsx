/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {createBatchOperation, mockProcessInstances} from 'modules/testUtils';
import {ListPanel} from '../index';
import {processInstancesStore} from 'modules/stores/processInstances';
import {panelStatesStore} from 'modules/stores/panelStates';
import {createWrapper} from './mocks';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockApplyBatchOperation} from 'modules/mocks/api/processInstances/operations';
import {act} from 'react-dom/test-utils';

describe('spinner', () => {
  it('should display spinners on batch operation', async () => {
    jest.useFakeTimers();

    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    mockApplyBatchOperation().withSuccess(createBatchOperation());

    processInstancesStore.fetchProcessInstancesFromFilters();

    const {user} = render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);
    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    await user.click(screen.getByLabelText(/select all instances/i));
    await user.click(screen.getByText(/apply operation on/i));
    await user.click(screen.getByText(/cancel/i));
    await user.click(screen.getByText(/^apply$/i));
    expect(screen.getAllByTestId('operation-spinner')).toHaveLength(3);

    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    act(() => {
      processInstancesStore.fetchProcessInstancesFromFilters();
    });

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );
    expect(screen.queryAllByTestId('operation-spinner')).toHaveLength(0);
    expect(panelStatesStore.state.isOperationsCollapsed).toBe(false);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should remove spinners after batch operation if a server error occurs', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    mockApplyBatchOperation().withServerError();

    processInstancesStore.fetchProcessInstancesFromFilters();

    const {user} = render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    mockFetchProcessInstances().withSuccess({
      ...mockProcessInstances,
      totalCount: 1000,
    });

    await user.click(screen.getByLabelText(/select all instances/i));
    await user.click(screen.getByText(/apply operation on/i));
    await user.click(screen.getByText(/cancel/i));
    await user.click(screen.getByText(/^apply$/i));

    await waitFor(() =>
      expect(screen.queryAllByTestId('operation-spinner')).toHaveLength(3)
    );
    await waitFor(() =>
      expect(screen.queryAllByTestId('operation-spinner')).toHaveLength(0)
    );

    expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);
  });

  it('should remove spinners after batch operation if a network error occurs', async () => {
    jest.useFakeTimers();

    mockApplyBatchOperation().withNetworkError();

    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    processInstancesStore.fetchProcessInstancesFromFilters();

    const {user} = render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    mockFetchProcessInstances().withSuccess({
      ...mockProcessInstances,
      totalCount: 1000,
    });

    await user.click(screen.getByLabelText(/select all instances/i));
    await user.click(screen.getByText(/apply operation on/i));
    await user.click(screen.getByText(/cancel/i));

    await user.click(screen.getByText(/^apply$/i));
    expect(screen.getAllByTestId('operation-spinner')).toHaveLength(3);

    await waitFor(() =>
      expect(screen.queryAllByTestId('operation-spinner')).toHaveLength(0)
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
