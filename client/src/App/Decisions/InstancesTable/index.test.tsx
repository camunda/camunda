/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  within,
  waitForElementToBeRemoved,
  waitFor,
} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
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

const createWrapper = (initialPath: string = '/decisions') => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      groupedDecisionsStore.fetchDecisions();
      return () => {
        decisionInstancesStore.reset();
        groupedDecisionsStore.reset();
      };
    }, []);

    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/decisions" element={children} />
            <Route path="/processes/:processInstanceId" element={<></>} />
            <Route path="/decisions/:decisionInstanceId" element={<></>} />
          </Routes>
          <LocationLog />
        </MemoryRouter>
      </ThemeProvider>
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

    expect(screen.getByTestId('table-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));
  });

  it('should render error message', async () => {
    mockFetchDecisionInstances().withServerError();

    render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
  });

  it('should render empty message when no filter is selected', async () => {
    mockFetchDecisionInstances().withSuccess({
      totalCount: 0,
      decisionInstances: [],
    });

    render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(
      screen.getByText('There are no Instances matching this filter set')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see some results, select at least one Instance state'
      )
    ).toBeInTheDocument();

    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
  });

  it('should render empty message when at least one filter is selected', async () => {
    mockFetchDecisionInstances().withSuccess({
      totalCount: 0,
      decisionInstances: [],
    });

    render(<InstancesTable />, {
      wrapper: createWrapper('/decisions?evaluated=true&failed=true'),
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

    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
  });

  it('should render decision instances', async () => {
    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

    render(<InstancesTable />, {wrapper: createWrapper()});

    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(
      screen.getByRole('columnheader', {
        name: 'Name',
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: 'Decision Instance Key',
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: 'Version',
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /Evaluation Date/,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: 'Process Instance Key',
      })
    ).toBeInTheDocument();

    const rows = screen.getAllByRole('row');
    expect(rows).toHaveLength(29);

    const [, firstDecisionInstance, secondDecisionInstance] = rows;
    expect(
      within(firstDecisionInstance!).getByText('test decision instance 1')
    ).toBeInTheDocument();
    expect(
      within(firstDecisionInstance!).getByTestId(
        'EVALUATED-icon-2251799813689541'
      )
    ).toBeInTheDocument();

    expect(
      within(secondDecisionInstance!).getByText('test decision instance 2')
    ).toBeInTheDocument();
    expect(
      within(secondDecisionInstance!).getByTestId(
        'FAILED-icon-2251799813689542'
      )
    ).toBeInTheDocument();
    expect(screen.getByText('2 results found')).toBeInTheDocument();
  });

  it('should navigate to decision instance page', async () => {
    jest.useFakeTimers();

    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

    const {user} = render(<InstancesTable />, {
      wrapper: createWrapper('/decisions'),
    });

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);

    await user.click(
      screen.getByRole('link', {
        description: /view decision instance 2251799813689541/i,
      })
    );

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        /^\/decisions\/2251799813689541$/
      )
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
        },
      ],
    });

    const {user} = render(<InstancesTable />, {
      wrapper: createWrapper('/decisions'),
    });

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);

    await user.click(
      screen.getByRole('link', {
        description: /view process instance 2251799813689544/i,
      })
    );

    await waitFor(() =>
      expect(screen.getByTestId('pathname')).toHaveTextContent(
        /^\/processes\/2251799813689544$/
      )
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should display loading skeleton when sorting is applied', async () => {
    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

    const {user} = render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();

    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

    await user.click(screen.getByRole('button', {name: 'Sort by Name'}));

    expect(await screen.findByTestId('instances-loader')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('instances-loader'));
  });

  it('should refetch data when navigated from header', async () => {
    mockFetchGroupedDecisions().withSuccess(mockGroupedDecisions);
    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

    const {user} = render(
      <>
        <AppHeader />
        <InstancesTable />
      </>,
      {wrapper: createWrapper()}
    );

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    mockFetchDecisionInstances().withSuccess(mockDecisionInstances);

    await user.click(
      within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        })
      ).getByRole('link', {
        name: /decisions/i,
      })
    );

    expect(await screen.findByTestId('instances-loader')).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument()
    );
  });
});
