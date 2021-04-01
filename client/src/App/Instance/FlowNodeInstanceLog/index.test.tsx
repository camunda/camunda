/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
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

mockServer.use(
  rest.get('/api/process-instances/:instanceId', (_, res, ctx) =>
    res.once(ctx.json({id: '1', state: 'ACTIVE', processName: 'processName'}))
  )
);

describe('FlowNodeInstanceLog', () => {
  beforeAll(async () => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(
          ctx.json({id: '1', state: 'ACTIVE', processName: 'processName'})
        )
      )
    );

    await currentInstanceStore.init('1');
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

    await singleInstanceDiagramStore.fetchProcessXml('1');
    flowNodeInstanceStore.fetchInstanceExecutionHistory('1');

    expect(screen.getByTestId('flownodeInstance-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('flownodeInstance-skeleton')
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

    flowNodeInstanceStore.fetchInstanceExecutionHistory('1');
    singleInstanceDiagramStore.fetchProcessXml('1');

    expect(
      await screen.findByTestId('flownodeInstance-skeleton')
    ).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('flownodeInstance-skeleton')
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

    singleInstanceDiagramStore.fetchProcessXml('1');
    flowNodeInstanceStore.fetchInstanceExecutionHistory('1');
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

    singleInstanceDiagramStore.fetchProcessXml('1');
    flowNodeInstanceStore.fetchInstanceExecutionHistory('1');

    expect(
      await screen.findByText('Instance History could not be fetched')
    ).toBeInTheDocument();
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

    singleInstanceDiagramStore.fetchProcessXml('1');
    flowNodeInstanceStore.fetchInstanceExecutionHistory('1');

    expect((await screen.findAllByText('processName')).length).toBeGreaterThan(
      0
    );
  });
});
