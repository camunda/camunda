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
} from 'modules/testing-library';
import {ListPanel} from '../index';
import {processInstancesStore} from 'modules/stores/processInstances';
import {createWrapper} from './mocks';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';

describe('messages', () => {
  it('should display a message for empty list when no filter is selected', async () => {
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    processInstancesStore.fetchProcessInstancesFromFilters();

    render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(
      screen.getByText('There are no Instances matching this filter set')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see some results, select at least one Instance state'
      )
    ).toBeInTheDocument();
  });

  it('should display a message for empty list when at least one filter is selected', async () => {
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    processInstancesStore.fetchProcessInstancesFromFilters();

    render(<ListPanel />, {
      wrapper: createWrapper('/processes?incidents=true&active=true'),
    });

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(
      screen.getByText('There are no Instances matching this filter set')
    ).toBeInTheDocument();
    expect(
      screen.queryByText(
        'To see some results, select at least one Instance state'
      )
    ).not.toBeInTheDocument();
  });
});
