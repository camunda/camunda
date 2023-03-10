/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
  waitFor,
} from 'modules/testing-library';
import {createBatchOperation} from 'modules/testUtils';
import {INSTANCE} from '../index.setup';
import {ListPanel} from '../index';
import {processInstancesStore} from 'modules/stores/processInstances';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {createWrapper} from './mocks';
import {act} from 'react-dom/test-utils';

describe('ListPanel', () => {
  it('should start operation on an instance from list', async () => {
    jest.useFakeTimers();

    mockFetchProcessInstances().withSuccess({
      processInstances: [INSTANCE],
      totalCount: 1,
    });

    mockApplyOperation().withSuccess(
      createBatchOperation({type: 'CANCEL_PROCESS_INSTANCE'})
    );

    processInstancesStore.fetchProcessInstancesFromFilters();
    processInstancesStore.init();

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    const {user} = render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.queryByTitle(/has scheduled operations/i)
    ).not.toBeInTheDocument();
    await user.click(screen.getByTitle('Cancel Instance 1'));
    await user.click(screen.getByTitle('Apply'));
    expect(
      screen.getByTitle(/instance 1 has scheduled operations/i)
    ).toBeInTheDocument();
    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

    mockFetchProcessInstances().withSuccess({
      processInstances: [INSTANCE],
      totalCount: 1,
    });

    act(() => {
      jest.runOnlyPendingTimers();
    });

    mockFetchProcessInstances().withSuccess({
      processInstances: [INSTANCE],
      totalCount: 1,
    });

    await waitForElementToBeRemoved(screen.getByTestId('operation-spinner'));

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
