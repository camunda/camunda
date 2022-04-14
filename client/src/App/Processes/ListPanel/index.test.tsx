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
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {groupedProcessesMock, mockProcessInstances} from 'modules/testUtils';
import {INSTANCE, ACTIVE_INSTANCE} from './index.setup';
import {ListPanel} from './index';
import {Link, MemoryRouter} from 'react-router-dom';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {processInstancesStore} from 'modules/stores/processInstances';
import {NotificationProvider} from 'modules/notifications';
import {processInstancesDiagramStore} from 'modules/stores/processInstancesDiagram';
import {authenticationStore} from 'modules/stores/authentication';
import {panelStatesStore} from 'modules/stores/panelStates';
import {ListFooter} from './ListFooter';

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ThemeProvider>
        <NotificationProvider>
          <MemoryRouter initialEntries={[initialPath]}>
            {children}
            <ListFooter />
            <Link to="/processes?incidents=true&active=true&process=bigVarProcess">
              go to big var
            </Link>
          </MemoryRouter>
        </NotificationProvider>
      </ThemeProvider>
    );
  };
  return Wrapper;
}

describe('ListPanel', () => {
  afterEach(() => {
    processInstancesStore.reset();
    processInstancesDiagramStore.reset();
    panelStatesStore.reset();
  });

  describe('messages', () => {
    it('should display a message for empty list when no filter is selected', async () => {
      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

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
      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

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

  describe('display instances List', () => {
    afterEach(() => {
      authenticationStore.reset();
    });

    it('should render a skeleton', async () => {
      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );
      processInstancesStore.fetchProcessInstancesFromFilters();

      render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByTestId('table-skeleton')).toBeInTheDocument();
      await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));
    });

    it('should render table body and footer', async () => {
      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(
            ctx.json({
              processInstances: [INSTANCE, ACTIVE_INSTANCE],
              totalCount: 2,
            })
          )
        )
      );

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
      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

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
      });

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(
            ctx.json({
              processInstances: [INSTANCE, ACTIVE_INSTANCE],
              totalCount: 0,
            })
          )
        )
      );

      processInstancesStore.fetchProcessInstancesFromFilters();

      render(<ListPanel />, {wrapper: createWrapper()});
      await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

      expect(
        screen.queryByTitle('Select all instances')
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole('checkbox', {name: /select instance/i})
      ).not.toBeInTheDocument();
      expect(screen.queryByText('Operations')).not.toBeInTheDocument();
    });
  });

  it('should start operation on an instance from list', async () => {
    jest.useFakeTimers();

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json({processInstances: [INSTANCE], totalCount: 1}))
      ),
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: '1',
            name: null,
            type: 'CANCEL_PROCESS_INSTANCE',
            startDate: '2020-11-23T04:42:08.030+0000',
            endDate: null,
            username: 'demo',
            instancesCount: 1,
            operationsTotalCount: 1,
            operationsFinishedCount: 0,
          })
        )
      )
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

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json({processInstances: [INSTANCE], totalCount: 1}))
      ),
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [INSTANCE],
            totalCount: 2,
          })
        )
      )
    );
    jest.runOnlyPendingTimers();
    await waitForElementToBeRemoved(screen.getByTestId('operation-spinner'));

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  describe('spinner', () => {
    it('should display spinners on batch operation', async () => {
      jest.useFakeTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json(mockProcessInstances))
        ),
        rest.post('/api/process-instances/batch-operation', (_, res, ctx) =>
          res.once(ctx.json({}))
        )
      );

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

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json(mockProcessInstances))
        )
      );

      processInstancesStore.fetchProcessInstancesFromFilters();

      await waitFor(() =>
        expect(processInstancesStore.state.status).toBe('fetched')
      );
      expect(screen.queryAllByTestId('operation-spinner')).toHaveLength(0);
      expect(panelStatesStore.state.isOperationsCollapsed).toBe(false);

      jest.clearAllTimers();
      jest.useRealTimers();
    });

    it('should remove spinners after batch operation if a server error occurs', async () => {
      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json(mockProcessInstances))
        ),
        rest.post('/api/process-instances/batch-operation', (_, res, ctx) =>
          res.once(ctx.status(500), ctx.json({}))
        ),
        rest.post('/api/process-instances', (_, res, ctx) =>
          res(ctx.json({...mockProcessInstances, totalCount: 1000}))
        )
      );

      processInstancesStore.fetchProcessInstancesFromFilters();

      const {user} = render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      await waitFor(() =>
        expect(processInstancesStore.state.status).toBe('fetched')
      );

      await user.click(screen.getByLabelText(/select all instances/i));
      await user.click(screen.getByText(/apply operation on/i));
      await user.click(screen.getByText(/cancel/i));
      await user.click(screen.getByText(/^apply$/i));
      expect(screen.getAllByTestId('operation-spinner')).toHaveLength(3);
      await waitFor(() =>
        expect(screen.queryAllByTestId('operation-spinner')).toHaveLength(0)
      );

      expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);
    });

    it('should remove spinners after batch operation if a network error occurs', async () => {
      jest.useFakeTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json(mockProcessInstances))
        ),
        rest.post('/api/process-instances/batch-operation', (_, res) =>
          res.networkError('A network error')
        ),
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({...mockProcessInstances, totalCount: 1000}))
        )
      );

      processInstancesStore.fetchProcessInstancesFromFilters();

      const {user} = render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      await waitFor(() =>
        expect(processInstancesStore.state.status).toBe('fetched')
      );

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

  it('should show an error message', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json({}), ctx.status(500))
      )
    );

    const {unmount} = render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    processInstancesStore.fetchProcessInstancesFromFilters();

    expect(
      await screen.findByText('Data could not be fetched')
    ).toBeInTheDocument();
    expect(
      screen.queryByText('There are no Instances matching this filter set')
    ).not.toBeInTheDocument();

    unmount();
    processInstancesStore.reset();

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.networkError('A network error')
      )
    );

    processInstancesStore.fetchProcessInstancesFromFilters();

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

  describe('getting started experience', () => {
    it('should poll until there is a process instance', async () => {
      jest.useFakeTimers();

      mockServer.use(
        rest.get('/api/processes/grouped', (_, res, ctx) =>
          res.once(ctx.json(groupedProcessesMock))
        ),
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      render(<ListPanel />, {
        wrapper: createWrapper('/processes?incidents=true&active=true'),
      });

      processInstancesStore.init(true);
      await processInstancesStore.fetchProcessInstancesFromFilters();

      expect(screen.getByTestId('table-skeleton')).toBeInTheDocument();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );
      jest.runOnlyPendingTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json(mockProcessInstances))
        )
      );

      jest.runOnlyPendingTimers();

      await waitFor(() =>
        expect(processInstancesStore.state.status).toBe('fetched')
      );
      expect(screen.queryByTestId('table-skeleton')).not.toBeInTheDocument();
      expect(screen.getByText('2251799813685594')).toBeInTheDocument();

      jest.clearAllTimers();
      jest.useRealTimers();
    });

    it('should poll 3 times, then display empty list message (on first render)', async () => {
      jest.useFakeTimers();

      mockServer.use(
        rest.get('/api/processes/grouped', (_, res, ctx) =>
          res.once(ctx.json(groupedProcessesMock))
        ),
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      render(<ListPanel />, {
        wrapper: createWrapper('/processes?incidents=true&active=true'),
      });

      processInstancesStore.init(true);
      await processInstancesStore.fetchProcessInstancesFromFilters();

      expect(screen.getByTestId('table-skeleton')).toBeInTheDocument();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );
      jest.runOnlyPendingTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      jest.runOnlyPendingTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      jest.runOnlyPendingTimers();

      await waitFor(() =>
        expect(processInstancesStore.state.status).toBe('fetched')
      );
      expect(screen.queryByTestId('table-skeleton')).not.toBeInTheDocument();
      expect(
        screen.getByText('There are no Instances matching this filter set')
      ).toBeInTheDocument();

      jest.clearAllTimers();
      jest.useRealTimers();
    });

    it('should poll 3 times, then display empty list message (on filter change)', async () => {
      jest.useFakeTimers();

      mockServer.use(
        rest.get('/api/processes/grouped', (_, res, ctx) =>
          res.once(ctx.json(groupedProcessesMock))
        ),
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json(mockProcessInstances))
        )
      );

      processInstancesStore.init(true);
      await processInstancesStore.fetchProcessInstancesFromFilters();

      const {user} = render(<ListPanel />, {
        wrapper: createWrapper('/processes?incidents=true&active=true'),
      });

      await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));
      expect(screen.getByText('2251799813685594')).toBeInTheDocument();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      await user.click(screen.getByText(/go to big var/i));
      processInstancesStore.fetchProcessInstancesFromFilters();

      await waitFor(() =>
        expect(processInstancesStore.state.status).toBe('refetching')
      );
      expect(screen.queryByTestId('table-skeleton')).not.toBeInTheDocument();
      expect(screen.getByTestId('instances-loader')).toBeInTheDocument();
      expect(
        screen.queryByText('There are no Instances matching this filter set')
      ).not.toBeInTheDocument();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );
      jest.runOnlyPendingTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      jest.runOnlyPendingTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      jest.runOnlyPendingTimers();

      await waitFor(() =>
        expect(processInstancesStore.state.status).toBe('fetched')
      );
      expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();

      expect(
        screen.getByText('There are no Instances matching this filter set')
      ).toBeInTheDocument();

      jest.clearAllTimers();
      jest.useRealTimers();
    });
  });
});
