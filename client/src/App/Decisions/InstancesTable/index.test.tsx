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
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {InstancesTable} from './index';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {mockDecisionInstances} from 'modules/mocks/mockDecisionInstances';
import {Routes, Route, MemoryRouter} from 'react-router-dom';
import {LocationLog} from 'modules/utils/LocationLog';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';
import {groupedDecisions as mockGroupedDecisions} from 'modules/mocks/groupedDecisions';
import {Header} from 'App/Layout/Header';

const createWrapper = (initialPath: string = '/decisions') => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
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
    mockServer.use(
      rest.get('/api/decisions/grouped', (_, res, ctx) =>
        res.once(ctx.json(mockGroupedDecisions))
      )
    );

    groupedDecisionsStore.fetchDecisions();
  });
  afterEach(() => {
    decisionInstancesStore.reset();
    groupedDecisionsStore.reset();
  });

  it('should initially render skeleton', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstances))
      )
    );

    render(<InstancesTable />, {wrapper: createWrapper()});

    expect(screen.getByTestId('table-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));
  });

  it('should render error message', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.status(500))
      )
    );

    render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
  });

  it('should render empty message when no filter is selected', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json({decisionInstances: []}))
      )
    );

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
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json({decisionInstances: []}))
      )
    );

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
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstances))
      )
    );

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
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstances))
      )
    );

    const {user} = render(<InstancesTable />, {
      wrapper: createWrapper('/decisions'),
    });

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);

    await user.click(
      screen.getByRole('link', {
        name: /view decision instance 2251799813689541/i,
      })
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/decisions\/2251799813689541$/
    );
  });

  it('should navigate to process instance page', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            decisionInstances: [
              {
                id: '2251799813689541',
                name: 'test decision instance 1',
                version: 1,
                evaluationDate: '2022-02-07T10:01:51.293+0000',
                processInstanceId: '2251799813689544',
                state: 'EVALUATED',
              },
            ],
          })
        )
      )
    );

    const {user} = render(<InstancesTable />, {
      wrapper: createWrapper('/decisions'),
    });

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/decisions$/);

    await user.click(
      screen.getByRole('link', {
        name: /view process instance 2251799813689544/i,
      })
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/processes\/2251799813689544$/
    );
  });

  it('should display loading skeleton when sorting is applied', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstances))
      )
    );

    const {user} = render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();

    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstances))
      )
    );

    await user.click(screen.getByRole('button', {name: 'Sort by Name'}));

    expect(screen.getByTestId('instances-loader')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('instances-loader'));
  });

  it('should refetch data when navigated from header', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstances))
      ),
      rest.get('/api/decisions/grouped', (_, res, ctx) =>
        res.once(ctx.json(mockGroupedDecisions))
      ),
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstances))
      )
    );

    const {user} = render(
      <>
        <Header />
        <InstancesTable />
      </>,
      {wrapper: createWrapper()}
    );

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    await user.click(screen.getByRole('link', {name: 'View Decisions'}));

    await waitFor(() =>
      expect(screen.getByTestId('instances-loader')).toBeInTheDocument()
    );

    await waitFor(() =>
      expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument()
    );
  });
});
