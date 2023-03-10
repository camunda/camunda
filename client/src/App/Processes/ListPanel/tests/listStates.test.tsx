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
import {authenticationStore} from 'modules/stores/authentication';
import {createWrapper, INSTANCE, ACTIVE_INSTANCE} from './mocks';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {act} from 'react-dom/test-utils';

describe('display instances List', () => {
  it('should render a skeleton', async () => {
    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    processInstancesStore.fetchProcessInstancesFromFilters();

    render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('table-skeleton')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));
  });

  it('should render table body and footer', async () => {
    mockFetchProcessInstances().withSuccess({
      processInstances: [INSTANCE, ACTIVE_INSTANCE],
      totalCount: 2,
    });

    processInstancesStore.fetchProcessInstancesFromFilters();

    render(<ListPanel />, {wrapper: createWrapper()});
    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.getByLabelText('Select all instances')).toBeInTheDocument();
    expect(
      screen.getAllByRole('checkbox', {name: /select instance/i}).length
    ).toBe(2);
    expect(screen.getByText('Operations')).toBeInTheDocument();
    expect(
      screen.getByText(/^© Camunda Services GmbH \d{4}. All rights reserved./)
    ).toBeInTheDocument();
    expect(screen.getByText('2 results found')).toBeInTheDocument();
  });

  it('should render Footer when list is empty', async () => {
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
      screen.getByText(/^© Camunda Services GmbH \d{4}. All rights reserved./)
    ).toBeInTheDocument();
    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
  });

  it('should render for restricted users', async () => {
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    mockFetchProcessInstances().withSuccess({
      processInstances: [INSTANCE, ACTIVE_INSTANCE],
      totalCount: 2,
    });

    processInstancesStore.fetchProcessInstancesFromFilters();

    render(<ListPanel />, {wrapper: createWrapper()});
    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.queryByTitle('Select all instances')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('checkbox', {name: /select instance/i})
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Operations')).not.toBeInTheDocument();
  });

  it('should show an error message', async () => {
    mockFetchProcessInstances().withServerError();

    const {unmount} = render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    act(() => {
      processInstancesStore.fetchProcessInstancesFromFilters();
    });

    expect(
      await screen.findByText('Data could not be fetched')
    ).toBeInTheDocument();
    expect(
      screen.queryByText('There are no Instances matching this filter set')
    ).not.toBeInTheDocument();

    unmount();
    processInstancesStore.reset();

    mockFetchProcessInstances().withNetworkError();

    act(() => {
      processInstancesStore.fetchProcessInstancesFromFilters();
    });

    render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched')
    ).toBeInTheDocument();
    expect(
      screen.queryByText('There are no Instances matching this filter set')
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
  });
});
