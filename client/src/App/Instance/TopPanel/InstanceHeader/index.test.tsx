/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {getProcessName} from 'modules/utils/instance';
import {InstanceHeader} from './index';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {variablesStore} from 'modules/stores/variables';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {operationsStore} from 'modules/stores/operations';
import {
  mockInstanceWithActiveOperation,
  mockInstanceWithoutOperations,
  mockInstanceWithParentInstance,
  mockOperationCreated,
  mockCanceledInstance,
} from './index.setup';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {mockCallActivityProcessXML, mockProcessXML} from 'modules/testUtils';
import {authenticationStore} from 'modules/stores/authentication';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';

jest.mock('modules/notifications', () => {
  const mockUseNotifications = {
    displayNotification: jest.fn(),
  };

  return {
    useNotifications: () => {
      return mockUseNotifications;
    },
  };
});

const Wrapper: React.FC = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>
        <Routes>
          <Route path="/processes/:processInstanceId" element={children} />
          <Route path="/processes" element={children} />
        </Routes>
        <LocationLog />
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('InstanceHeader', () => {
  afterEach(() => {
    operationsStore.reset();
    variablesStore.reset();
    currentInstanceStore.reset();
    singleInstanceDiagramStore.reset();
    authenticationStore.reset();
  });

  it('should show skeleton before instance data is available', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    render(<InstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    singleInstanceDiagramStore.init();
    currentInstanceStore.init({
      id: mockInstanceWithActiveOperation.id,
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
  });

  it('should render instance data', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );
    render(<InstanceHeader />, {wrapper: Wrapper});

    singleInstanceDiagramStore.init();
    currentInstanceStore.init({
      id: mockInstanceWithActiveOperation.id,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
    const {instance} = currentInstanceStore.state;

    const processName = getProcessName(instance);
    const instanceState = mockInstanceWithActiveOperation.state;

    expect(screen.getByText(processName)).toBeInTheDocument();
    expect(
      screen.getByText(mockInstanceWithActiveOperation.id)
    ).toBeInTheDocument();
    expect(
      screen.getByText(mockInstanceWithActiveOperation.processVersion)
    ).toBeInTheDocument();
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(screen.getByText('--')).toBeInTheDocument();
    expect(screen.getByTestId(`${instanceState}-icon`)).toBeInTheDocument();
    expect(screen.getByText('Process')).toBeInTheDocument();
    expect(screen.getByText('Instance Id')).toBeInTheDocument();
    expect(screen.getByText('Version')).toBeInTheDocument();
    expect(screen.getByText('Start Date')).toBeInTheDocument();
    expect(screen.getByText('End Date')).toBeInTheDocument();
    expect(screen.getByText('Parent Instance Id')).toBeInTheDocument();
    expect(screen.getByText('Called Instances')).toBeInTheDocument();
    expect(screen.getAllByText('None').length).toBe(2);
    expect(
      screen.queryByRole('link', {name: /view all/i})
    ).not.toBeInTheDocument();
  });

  it('should render "View All" link for call activity process', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockCallActivityProcessXML))
      )
    );

    render(<InstanceHeader />, {wrapper: Wrapper});

    singleInstanceDiagramStore.init();
    currentInstanceStore.init({
      id: mockInstanceWithActiveOperation.id,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
    expect(
      await screen.findByRole('link', {name: /view all/i})
    ).toBeInTheDocument();
  });

  it('should navigate to Instances Page and expand Filters Panel on "View All" click', async () => {
    panelStatesStore.toggleFiltersPanel();

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockCallActivityProcessXML))
      )
    );

    render(<InstanceHeader />, {wrapper: Wrapper});

    singleInstanceDiagramStore.init();
    currentInstanceStore.init({
      id: mockInstanceWithActiveOperation.id,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent('/processes/1');
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    userEvent.click(await screen.findByRole('link', {name: /view all/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent('/processes');
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });

  it('should render parent instance id', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithParentInstance))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );
    render(<InstanceHeader />, {wrapper: Wrapper});

    singleInstanceDiagramStore.init();
    currentInstanceStore.init({id: mockInstanceWithParentInstance.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(
      screen.getByRole('link', {
        name: `View parent instance ${mockInstanceWithParentInstance.parentInstanceId}`,
      })
    ).toBeInTheDocument();
  });

  it('should show spinner based on instance having active operations', async () => {
    render(<InstanceHeader />, {wrapper: Wrapper});

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithoutOperations))
      ),
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    jest.useFakeTimers();
    singleInstanceDiagramStore.init();
    currentInstanceStore.init({id: mockInstanceWithoutOperations.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    jest.runOnlyPendingTimers();

    expect(await screen.findByTestId('operation-spinner')).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should show spinner when operation is applied', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithoutOperations))
      ),
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.json(mockOperationCreated))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    render(<InstanceHeader />, {wrapper: Wrapper});

    singleInstanceDiagramStore.init();
    currentInstanceStore.init({id: mockInstanceWithoutOperations.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    userEvent.click(screen.getByRole('button', {name: /Cancel Instance/}));
    userEvent.click(screen.getByRole('button', {name: 'Apply'}));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should show spinner when variables is updated', async () => {
    const mockVariable = {
      name: 'key',
      value: 'value',
      hasActiveOperation: false,
    };

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithoutOperations))
      ),
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res.once(ctx.json([mockVariable]))
      ),
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.json(undefined))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    render(<InstanceHeader />, {wrapper: Wrapper});
    singleInstanceDiagramStore.init();
    currentInstanceStore.init({
      id: mockInstanceWithActiveOperation.id,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    variablesStore.addVariable({
      id: mockInstanceWithoutOperations.id,
      name: mockVariable.name,
      value: mockVariable.value,
      onSuccess: () => {},
      onError: () => {},
    });

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: mockInstanceWithActiveOperation.id,
      payload: {pageSize: 10, scopeId: '1'},
    });

    await waitForElementToBeRemoved(screen.queryByTestId('operation-spinner'));
  });

  it('should remove spinner when operation fails', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithoutOperations))
      ),
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json({error: 'an error occured'}))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );
    render(<InstanceHeader />, {wrapper: Wrapper});
    singleInstanceDiagramStore.init();
    currentInstanceStore.init({id: mockInstanceWithoutOperations.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    userEvent.click(screen.getByRole('button', {name: /Cancel Instance/}));
    userEvent.click(screen.getByRole('button', {name: /Apply/}));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('operation-spinner'));
  });

  it('should show operation buttons when user has permission', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read', 'write'],
      canLogout: true,
      userId: 'demo',
    });

    render(<InstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    singleInstanceDiagramStore.init();
    currentInstanceStore.init({
      id: mockInstanceWithActiveOperation.id,
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(
      screen.getByRole('button', {name: /Cancel Instance/})
    ).toBeInTheDocument();
  });

  it('should hide operation buttons when user has no permission', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockInstanceWithActiveOperation))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
    });

    render(<InstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    singleInstanceDiagramStore.init();
    currentInstanceStore.init({
      id: mockInstanceWithActiveOperation.id,
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(
      screen.queryByRole('button', {name: /Cancel Instance/})
    ).not.toBeInTheDocument();
  });

  it('should display notification and redirect if delete operation is performed', async () => {
    jest.useFakeTimers();
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockCanceledInstance))
      ),
      rest.get('/api/processes/:id/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );
    const onPollingFailure = jest.fn();

    render(<InstanceHeader />, {wrapper: Wrapper});

    singleInstanceDiagramStore.init();

    currentInstanceStore.init({
      id: mockInstanceWithoutOperations.id,
      onPollingFailure,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent('/processes/1');
    userEvent.click(screen.getByRole('button', {name: /Delete Instance/}));
    expect(screen.getByText(/About to delete Instance/)).toBeInTheDocument();

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.json({}))
      )
    );

    userEvent.click(screen.getByTestId('delete-button'));
    await waitForElementToBeRemoved(
      screen.getByText(/About to delete Instance/)
    );

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.status(404), ctx.json({}))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => expect(onPollingFailure).toHaveBeenCalled());

    expect(screen.getByTestId('pathname')).toHaveTextContent('/processes');

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
