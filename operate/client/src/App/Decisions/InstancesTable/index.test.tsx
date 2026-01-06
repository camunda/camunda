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
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {mockDecisionInstances} from 'modules/mocks/mockDecisionInstances';
import {Routes, Route, MemoryRouter} from 'react-router-dom';
import {LocationLog} from 'modules/utils/LocationLog';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {groupedDecisions as mockGroupedDecisions} from 'modules/mocks/groupedDecisions';
import {AppHeader} from 'App/Layout/AppHeader';
import {mockFetchGroupedDecisions} from 'modules/mocks/api/decisions/fetchGroupedDecisions';
import {mockFetchDecisionInstances} from 'modules/mocks/api/decisionInstances/fetchDecisionInstances';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';

const createWrapper = (initialPath: string = Paths.decisions()) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      groupedDecisionsStore.fetchDecisions();
      return () => {
        decisionInstancesStore.reset();
        groupedDecisionsStore.reset();
      };
    }, []);

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
    mockFetchGroupedDecisions().withSuccess(mockGroupedDecisions);
    mockMe().withSuccess(createUser({authorizedComponents: ['operate']}));
    mockFetchDecisionInstances().withSuccess({
      totalCount: 0,
      decisionInstances: [],
    });
  });

  it('should initially render skeleton', async () => {
    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

    render(<InstancesTable />, {wrapper: createWrapper()});

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );
  });

  it('should render error message', async () => {
    mockFetchDecisionInstances().withServerError();

    render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
  });

  it('should render empty message when no filter is selected', async () => {
    mockFetchDecisionInstances().withSuccess({
      totalCount: 0,
      decisionInstances: [],
    });

    render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(
      screen.getByText('There are no Instances matching this filter set'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see some results, select at least one Instance state',
      ),
    ).toBeInTheDocument();

    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
  });

  it('should render empty message when at least one filter is selected', async () => {
    mockFetchDecisionInstances().withSuccess({
      totalCount: 0,
      decisionInstances: [],
    });

    render(<InstancesTable />, {
      wrapper: createWrapper(`${Paths.decisions()}?evaluated=true&failed=true`),
    });

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
  });

  it('should render decision instances', async () => {
    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

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
    expect(rows).toHaveLength(29);

    const [, firstDecisionInstance, secondDecisionInstance] = rows;
    expect(
      within(firstDecisionInstance!).getByText('test decision instance 1'),
    ).toBeInTheDocument();
    expect(
      within(firstDecisionInstance!).getByTestId(
        'EVALUATED-icon-2251799813689541',
      ),
    ).toBeInTheDocument();

    expect(
      within(secondDecisionInstance!).getByText('test decision instance 2'),
    ).toBeInTheDocument();
    expect(
      within(secondDecisionInstance!).getByTestId(
        'FAILED-icon-2251799813689542',
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

    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

    const {user} = render(<InstancesTable />, {
      wrapper: createWrapper(Paths.decisions()),
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);

    await user.click(
      screen.getByRole('link', {
        name: /view decision instance 2251799813689541/i,
      }),
    );

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        /^\/decisions\/2251799813689541$/,
      ),
    );

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should navigate to process instance page', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockFetchDecisionInstances().withSuccess({
      totalCount: 1,
      decisionInstances: [
        {
          id: '2251799813689541',
          decisionName: 'test decision instance 1',
          decisionVersion: 1,
          evaluationDate: '2022-02-07T10:01:51.293+0000',
          processInstanceId: '2251799813689544',
          state: 'EVALUATED',
          sortValues: ['', ''],
          tenantId: '',
        },
      ],
    });

    const {user} = render(<InstancesTable />, {
      wrapper: createWrapper(Paths.decisions()),
    });

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);

    await user.click(
      screen.getByRole('link', {
        name: /view process instance 2251799813689544/i,
      }),
    );

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        /^\/processes\/2251799813689544$/,
      ),
    );

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should display loading skeleton when sorting is applied', async () => {
    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

    const {user} = render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();

    mockFetchDecisionInstances().withDelay(mockDecisionInstances);

    await user.click(screen.getByRole('button', {name: 'Sort by Name'}));

    expect(screen.getByTestId('data-table-loader')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.queryByTestId('data-table-loader'));
  });

  it('should refetch data when navigated from header', async () => {
    mockFetchGroupedDecisions().withSuccess(mockGroupedDecisions);
    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

    const {user} = render(
      <>
        <AppHeader />
        <InstancesTable />
      </>,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    mockFetchDecisionInstances().withDelay(mockDecisionInstances);

    await user.click(
      within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        }),
      ).getByRole('link', {
        name: /decisions/i,
      }),
    );

    expect(screen.getByTestId('data-table-loader')).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument(),
    );
  });

  it.each(['all', undefined])(
    'should show tenant column when multi tenancy is enabled and tenant filter is %p',
    async (tenant) => {
      mockFetchDecisionInstances().withSuccess({
        totalCount: 0,
        decisionInstances: [],
      });

      vi.stubGlobal('clientConfig', {
        multiTenancyEnabled: true,
      });

      render(<InstancesTable />, {
        wrapper: createWrapper(
          `${Paths.decisions()}?${new URLSearchParams(
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
    mockFetchDecisionInstances().withSuccess({
      totalCount: 0,
      decisionInstances: [],
    });

    vi.stubGlobal('clientConfig', {
      multiTenancyEnabled: true,
    });

    render(<InstancesTable />, {
      wrapper: createWrapper(
        `${Paths.decisions()}?${new URLSearchParams({tenant: 'tenant-a'})}`,
      ),
    });

    expect(
      screen.queryByRole('columnheader', {name: 'Tenant'}),
    ).not.toBeInTheDocument();
  });

  it('should hide tenant column when multi tenancy is disabled', async () => {
    mockFetchDecisionInstances().withSuccess({
      totalCount: 0,
      decisionInstances: [],
    });

    render(<InstancesTable />, {
      wrapper: createWrapper(
        `${Paths.decisions()}?${new URLSearchParams({tenant: 'all'})}`,
      ),
    });

    expect(
      screen.queryByRole('columnheader', {name: 'Tenant'}),
    ).not.toBeInTheDocument();
  });
});
