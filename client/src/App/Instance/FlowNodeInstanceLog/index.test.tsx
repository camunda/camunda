/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from '@testing-library/react';

import {FlowNodeInstanceLog} from './index';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {createMultiInstanceFlowNodeInstances} from 'modules/testUtils';

jest.mock('modules/utils/bpmn');

const processInstancesMock = createMultiInstanceFlowNodeInstances('1');

describe('FlowNodeInstanceLog', () => {
  beforeAll(async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(
          ctx.json({id: '1', state: 'ACTIVE', processName: 'processName'})
        )
      )
    );

    currentInstanceStore.init({id: '1'});
  });

  afterAll(() => {
    currentInstanceStore.reset();
  });

  afterEach(() => {
    flowNodeInstanceStore.reset();
    singleInstanceDiagramStore.reset();
  });

  it('should render skeleton when instance tree is not loaded', async () => {
    mockServer.use(
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(processInstancesMock.level1))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      )
    );

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    flowNodeInstanceStore.init();
    singleInstanceDiagramStore.fetchProcessXml('1');

    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-history-skeleton')
    );
  });

  it('should render skeleton when instance diagram is not loaded', async () => {
    mockServer.use(
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(processInstancesMock.level1))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      )
    );

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    flowNodeInstanceStore.init();
    singleInstanceDiagramStore.fetchProcessXml('1');

    expect(
      await screen.findByTestId('instance-history-skeleton')
    ).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-history-skeleton')
    );
  });

  it('should display error when instance tree data could not be fetched', async () => {
    mockServer.use(
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json({}), ctx.status(500))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      )
    );

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    flowNodeInstanceStore.init();
    singleInstanceDiagramStore.fetchProcessXml('1');
    expect(
      await screen.findByText('Instance History could not be fetched')
    ).toBeInTheDocument();
  });

  it('should display error when instance diagram could not be fetched', async () => {
    mockServer.use(
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(processInstancesMock.level1))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.text(''))
      )
    );

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    flowNodeInstanceStore.init();
    singleInstanceDiagramStore.fetchProcessXml('1');

    expect(
      await screen.findByText('Instance History could not be fetched')
    ).toBeInTheDocument();
  });

  it('should continue polling after poll failure', async () => {
    mockServer.use(
      // initial fetch
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(processInstancesMock.level1))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      )
    );
    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    jest.useFakeTimers();
    flowNodeInstanceStore.init();
    singleInstanceDiagramStore.fetchProcessXml('1');

    expect(await screen.findAllByTestId('INCIDENT-icon')).toHaveLength(1);
    expect(await screen.findAllByTestId('COMPLETED-icon')).toHaveLength(1);

    mockServer.use(
      // currentInstanceStore poll
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res(ctx.json({id: '1', state: 'ACTIVE', processName: 'processName'}))
      ),
      // first poll
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.text(''))
      )
    );
    jest.runOnlyPendingTimers();

    mockServer.use(
      // second poll
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(processInstancesMock.level1Poll))
      )
    );
    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(screen.queryByTestId('INCIDENT-icon')).not.toBeInTheDocument();
      expect(screen.getAllByTestId('COMPLETED-icon')).toHaveLength(2);
    });

    expect(
      screen.queryByText('Instance History could not be fetched')
    ).not.toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should render flow node instances tree', async () => {
    mockServer.use(
      rest.post('/api/flow-node-instances', (_, res, ctx) =>
        res.once(ctx.json(processInstancesMock.level1))
      ),
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      )
    );

    render(<FlowNodeInstanceLog />, {wrapper: ThemeProvider});

    flowNodeInstanceStore.init();
    singleInstanceDiagramStore.fetchProcessXml('1');

    expect((await screen.findAllByText('processName')).length).toBeGreaterThan(
      0
    );
  });
});
