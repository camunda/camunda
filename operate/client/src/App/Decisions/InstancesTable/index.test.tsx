/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  within,
  waitForElementToBeRemoved,
  waitFor,
} from 'modules/testing-library';
import {InstancesTable} from './index';
import {Routes, Route, MemoryRouter} from 'react-router-dom';
import {LocationLog} from 'modules/utils/LocationLog';
import {Paths} from 'modules/Routes';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {mockSearchDecisionInstances} from 'modules/mocks/api/v2/decisionInstances/searchDecisionInstances';
import {
  mockDecisionInstancesSearchResult,
  mockEmptyDecisionInstancesSearchResult,
} from 'modules/mocks/mockDecisionInstanceSearch';
import {
  assignApproverGroup,
  invoiceClassification,
} from 'modules/mocks/mockDecisionInstance';
import * as clientConfig from 'modules/utils/getClientConfig';

const createWrapper = (
  initialPath: string = `${Paths.decisions()}?evaluated=true`,
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path={Paths.decisions()} element={children} />
            <Route path={Paths.processInstance()} element={<></>} />
            <Route path={Paths.decisionInstance()} element={<></>} />
          </Routes>
          <LocationLog />
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
};

describe('<InstancesTable />', () => {
  beforeEach(() => {
    mockSearchDecisionInstances().withSuccess(
      mockDecisionInstancesSearchResult,
    );
  });

  it('should initially render skeleton', async () => {
    render(<InstancesTable />, {wrapper: createWrapper()});

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );
  });

  it('should render error message when searching instances fails', async () => {
    mockSearchDecisionInstances().withServerError();

    render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
  });

  it('should render empty message and load no instances when no filters are selected', async () => {
    const resolver = vi.fn();
    mockSearchDecisionInstances().withSuccess(
      mockDecisionInstancesSearchResult,
      {mockResolverFn: resolver},
    );

    render(<InstancesTable />, {wrapper: createWrapper(Paths.decisions())});

    expect(
      screen.getByText('There are no Instances matching this filter set'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see some results, select at least one Instance state',
      ),
    ).toBeInTheDocument();

    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
    expect(resolver).not.toHaveBeenCalled();
  });

  it('should render empty message when no results match the filter', async () => {
    const resolver = vi.fn();
    mockSearchDecisionInstances().withSuccess(
      mockEmptyDecisionInstancesSearchResult,
      {mockResolverFn: resolver},
    );

    render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(
      screen.getByText('There are no Instances matching this filter set'),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(
        'To see some results, select at least one Instance state',
      ),
    ).not.toBeInTheDocument();

    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
    expect(resolver).toHaveBeenCalled();
  });

  it('should render decision instances', async () => {
    render(<InstancesTable />, {wrapper: createWrapper()});

    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(
      screen.getByRole('columnheader', {
        name: /Name/,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /Decision Instance Key/,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /Version/,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /Evaluation Date/,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /Process Instance Key/,
      }),
    ).toBeInTheDocument();

    const rows = screen.getAllByRole('row');
    expect(rows).toHaveLength(3);

    const [, firstDecisionInstance, secondDecisionInstance] = rows;
    expect(
      within(firstDecisionInstance).getByText(
        invoiceClassification.decisionDefinitionName,
      ),
    ).toBeInTheDocument();
    expect(
      within(firstDecisionInstance).getByTestId(
        `EVALUATED-icon-${invoiceClassification.decisionEvaluationInstanceKey}`,
      ),
    ).toBeInTheDocument();

    expect(
      within(secondDecisionInstance!).getByText(
        assignApproverGroup.decisionDefinitionName,
      ),
    ).toBeInTheDocument();
    expect(
      within(secondDecisionInstance!).getByTestId(
        `FAILED-icon-${assignApproverGroup.decisionEvaluationInstanceKey}`,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: /Decision Instances - 2 results/i,
      }),
    ).toBeInTheDocument();
  });

  it('should navigate to decision instance page', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const {user} = render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);

    await user.click(
      screen.getByRole('link', {
        name: /view decision instance 30945876576324-1/i,
      }),
    );

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        /^\/decisions\/30945876576324-1$/,
      ),
    );

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should navigate to process instance page', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const {user} = render(<InstancesTable />, {
      wrapper: createWrapper(),
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);

    await user.click(
      screen.getByRole('link', {
        name: /view process instance 777/i,
      }),
    );

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        /^\/processes\/777$/,
      ),
    );

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should display loading skeleton when sorting is applied', async () => {
    const {user} = render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();

    mockSearchDecisionInstances().withDelay(mockDecisionInstancesSearchResult);

    await user.click(screen.getByRole('button', {name: 'Sort by Name'}));

    expect(screen.getByTestId('data-table-loader')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.queryByTestId('data-table-loader'));
  });

  it.each(['all', undefined])(
    'should show tenant column when multi tenancy is enabled and tenant filter is %p',
    async (tenant) => {
      vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
        ...clientConfig.getClientConfig(),
        multiTenancyEnabled: true,
      });

      render(<InstancesTable />, {
        wrapper: createWrapper(
          `${Paths.decisions()}?${new URLSearchParams(
            tenant === undefined
              ? {evaluated: 'true'}
              : {tenant, evaluated: 'true'},
          )}`,
        ),
      });

      expect(
        screen.getByRole('columnheader', {name: /Tenant/}),
      ).toBeInTheDocument();
    },
  );

  it('should hide tenant column when multi tenancy is enabled and tenant filter is a specific tenant', async () => {
    vi.spyOn(clientConfig, 'getClientConfig').mockReturnValue({
      ...clientConfig.getClientConfig(),
      multiTenancyEnabled: true,
    });

    render(<InstancesTable />, {
      wrapper: createWrapper(
        `${Paths.decisions()}?${new URLSearchParams({tenant: 'tenant-a', evaluated: 'true'})}`,
      ),
    });

    expect(
      screen.queryByRole('columnheader', {name: /Tenant/}),
    ).not.toBeInTheDocument();
  });

  it('should hide tenant column when multi tenancy is disabled', async () => {
    render(<InstancesTable />, {
      wrapper: createWrapper(
        `${Paths.decisions()}?${new URLSearchParams({tenant: 'all', evaluated: 'true'})}`,
      ),
    });

    expect(
      screen.queryByRole('columnheader', {name: /Tenant/}),
    ).not.toBeInTheDocument();
  });
});
