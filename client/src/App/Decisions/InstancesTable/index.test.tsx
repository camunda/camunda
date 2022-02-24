/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  render,
  screen,
  within,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {InstancesTable} from './index';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {mockDecisionInstances} from 'modules/mocks/mockDecisionInstances';
import {Routes, Route, MemoryRouter} from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import {LocationLog} from 'modules/utils/LocationLog';

const createWrapper = (initialPath: string = '/decisions') => {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/decisions" element={children} />
            <Route path="/instances/:processInstanceId" element={<></>} />
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
  afterEach(() => {
    decisionInstancesStore.reset();
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

  it('should render empty message', async () => {
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
        name: 'Decision',
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: 'Decision Instance Id',
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: 'Version',
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: /Evaluation Time/,
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: 'Process Instance Id',
      })
    ).toBeInTheDocument();

    const rows = screen.getAllByRole('row');
    expect(rows).toHaveLength(29);

    const [, firstDecisionInstance, secondDecisionInstance] = rows;
    expect(
      within(firstDecisionInstance).getByText('test decision instance 1')
    ).toBeInTheDocument();
    expect(
      within(firstDecisionInstance).getByTestId(
        'COMPLETED-icon-2251799813689541'
      )
    ).toBeInTheDocument();

    expect(
      within(secondDecisionInstance).getByText('test decision instance 2')
    ).toBeInTheDocument();
    expect(
      within(secondDecisionInstance).getByTestId('FAILED-icon-2251799813689542')
    ).toBeInTheDocument();
    expect(screen.getByText('2 results found')).toBeInTheDocument();
  });

  it('should navigate to decision instance page', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstances))
      )
    );

    render(<InstancesTable />, {wrapper: createWrapper('/decisions')});

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/decisions');

    userEvent.click(
      screen.getByRole('link', {
        name: /view decision instance 2251799813689541/i,
      })
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/decisions/2251799813689541'
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
                evaluationTime: '2022-02-07T10:01:51.293+0000',
                processInstanceId: '2251799813689544',
                state: 'COMPLETED',
              },
            ],
          })
        )
      )
    );

    render(<InstancesTable />, {wrapper: createWrapper('/decisions')});

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/decisions');

    userEvent.click(
      screen.getByRole('link', {
        name: /view process instance 2251799813689544/i,
      })
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      '/instances/2251799813689544'
    );
  });

  it('should display loading skeleton when sorting is applied', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstances))
      )
    );

    render(<InstancesTable />, {wrapper: createWrapper()});

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();

    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstances))
      )
    );

    userEvent.click(screen.getByRole('button', {name: 'Sort by Decision'}));

    expect(screen.getByTestId('instances-loader')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('instances-loader'));
  });
});
