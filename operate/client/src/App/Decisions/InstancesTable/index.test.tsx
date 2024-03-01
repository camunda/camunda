/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path={Paths.decisions()} element={children} />
          <Route path={Paths.processInstance()} element={<></>} />
          <Route path={Paths.decisionInstance()} element={<></>} />
        </Routes>
        <LocationLog />
      </MemoryRouter>
    );
  };

  return Wrapper;
};

describe('<InstancesTable />', () => {
  beforeEach(() => {
    mockFetchGroupedDecisions().withSuccess(mockGroupedDecisions);
  });

  it('should initially render skeleton', async () => {
    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

    render(<InstancesTable />, {wrapper: createWrapper()});

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));
  });

  it('should render error message', async () => {
    mockFetchDecisionInstances().withServerError();

    render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));

    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
  });

  it('should render empty message when no filter is selected', async () => {
    mockFetchDecisionInstances().withSuccess({
      totalCount: 0,
      decisionInstances: [],
    });

    render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));

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

    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));

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
    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));

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
    jest.useFakeTimers();

    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

    const {user} = render(<InstancesTable />, {
      wrapper: createWrapper(Paths.decisions()),
    });

    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));

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

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should navigate to process instance page', async () => {
    jest.useFakeTimers();

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

    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));

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

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should display loading skeleton when sorting is applied', async () => {
    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

    const {user} = render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));

    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();

    mockFetchDecisionInstances().withDelay(mockDecisionInstances);

    await user.click(screen.getByRole('button', {name: 'Sort by Name'}));

    expect(screen.getByTestId('data-table-loader')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('data-table-loader'));
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

    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));

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
      window.clientConfig = {
        multiTenancyEnabled: true,
      };

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

      window.clientConfig = undefined;
    },
  );

  it('should hide tenant column when multi tenancy is enabled and tenant filter is a specific tenant', async () => {
    window.clientConfig = {
      multiTenancyEnabled: true,
    };

    render(<InstancesTable />, {
      wrapper: createWrapper(
        `${Paths.decisions()}?${new URLSearchParams({tenant: 'tenant-a'})}`,
      ),
    });

    expect(
      screen.queryByRole('columnheader', {name: 'Tenant'}),
    ).not.toBeInTheDocument();

    window.clientConfig = undefined;
  });

  it('should hide tenant column when multi tenancy is disabled', async () => {
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
