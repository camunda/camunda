/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Router, Route} from 'react-router-dom';
import {
  render,
  waitForElementToBeRemoved,
  screen,
  waitFor,
} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {testData} from './index.setup';
import {mockSequenceFlows} from './TopPanel/index.setup';
import {PAGE_TITLE} from 'modules/constants';
import {getProcessName} from 'modules/utils/instance';
import {Instance} from './index';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {createMultiInstanceFlowNodeInstances} from 'modules/testUtils';
import {createMemoryHistory} from 'history';
import {useNotifications} from 'modules/notifications';

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

jest.mock('modules/utils/bpmn');

type Props = {
  children?: React.ReactNode;
};

const processInstancesMock = createMultiInstanceFlowNodeInstances('4294980768');

function getWrapper(
  history = createMemoryHistory({
    initialEntries: ['/instances/4294980768'],
  })
) {
  const Wrapper: React.FC<Props> = ({children}) => {
    return (
      <ThemeProvider>
        <Router history={history}>
          <Route path="/instances/:processInstanceId">{children} </Route>
        </Router>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('Instance', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res(ctx.text(''))
      ),
      rest.get(
        '/api/process-instances/:instanceId/sequence-flows',
        (_, res, ctx) => res(ctx.json(mockSequenceFlows))
      ),
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res(ctx.json(processInstancesMock.level1))
      ),
      rest.get(
        '/api/process-instances/:instanceId/flow-node-states',
        (_, rest, ctx) => rest(ctx.json({}))
      ),
      rest.get('/api/process-instances/core-statistics', (_, res, ctx) =>
        res(
          ctx.json({
            running: 821,
            active: 90,
            withIncidents: 731,
          })
        )
      ),
      rest.post('/api/process-instances/:instanceId/variables', (_, res, ctx) =>
        res(
          ctx.json([
            {
              id: '2251799813686037-mwst',
              name: 'newVariable',
              value: '1234',
              scopeId: '2251799813686037',
              processInstanceId: '2251799813686037',
              hasActiveOperation: false,
            },
          ])
        )
      )
    );
  });

  it('should render and set the page title', async () => {
    jest.useFakeTimers();

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(testData.fetch.onPageLoad.processInstance))
      )
    );

    render(<Instance />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-panel-body')).toBeInTheDocument();
    expect(screen.getByText('Instance History')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByText('newVariable')).toBeInTheDocument()
    );

    expect(document.title).toBe(
      PAGE_TITLE.INSTANCE(
        testData.fetch.onPageLoad.processInstance.id,
        getProcessName(testData.fetch.onPageLoad.processInstance)
      )
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should display skeletons until instance is available', async () => {
    jest.useFakeTimers();

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.status(404), ctx.json({}))
      )
    );

    render(<Instance />, {wrapper: getWrapper()});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('flownodeInstance-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(testData.fetch.onPageLoad.processInstance))
      )
    );

    jest.runOnlyPendingTimers();
    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('flownodeInstance-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('flownodeInstance-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.queryByTestId('diagram-spinner')).not.toBeInTheDocument();
      expect(
        screen.queryByTestId('flownodeInstance-skeleton')
      ).not.toBeInTheDocument();
      expect(screen.queryByTestId('skeleton-rows')).not.toBeInTheDocument();
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should poll 3 times for not found instance, then redirect to instances page and display notification', async () => {
    const mockHistory = createMemoryHistory({
      initialEntries: ['/instances/123'],
    });

    jest.useFakeTimers();

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res(ctx.status(404), ctx.json({}))
      )
    );

    render(<Instance />, {wrapper: getWrapper(mockHistory)});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('flownodeInstance-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('skeleton-rows')).toBeInTheDocument();

    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(mockHistory.location.pathname).toBe('/instances');
      expect(mockHistory.location.search).toBe('?active=true&incidents=true');
    });

    expect(useNotifications().displayNotification).toHaveBeenCalledWith(
      'error',
      {
        headline: 'Instance 123 could not be found',
      }
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
