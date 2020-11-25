/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {currentInstanceStore} from './currentInstance';
import {createInstance} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {waitFor} from '@testing-library/react';

const currentInstanceMock = createInstance();

describe('stores/currentInstance', () => {
  afterEach(() => {
    currentInstanceStore.reset();
  });

  beforeEach(() => {
    mockServer.use(
      rest.get('/api/workflow-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(currentInstanceMock))
      )
    );
  });

  it('should fetch current instance on init state', async () => {
    await currentInstanceStore.init(1);
    expect(currentInstanceStore.state.instance).toEqual(currentInstanceMock);
  });

  it('should poll if current instance is running', async () => {
    jest.useFakeTimers();
    await currentInstanceStore.init(1);

    const secondCurrentInstanceMock = createInstance();

    mockServer.use(
      rest.get('/api/workflow-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(secondCurrentInstanceMock))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(currentInstanceStore.state.instance).toEqual(
        secondCurrentInstanceMock
      )
    );

    const thirdCurrentInstanceMock = createInstance();

    mockServer.use(
      rest.get('/api/workflow-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(thirdCurrentInstanceMock))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(currentInstanceStore.state.instance).toEqual(
        thirdCurrentInstanceMock
      )
    );

    const finishedCurrentInstanceMock = createInstance({state: 'CANCELED'});

    mockServer.use(
      rest.get('/api/workflow-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(finishedCurrentInstanceMock))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() =>
      expect(currentInstanceStore.state.instance).toEqual(
        finishedCurrentInstanceMock
      )
    );

    // do not poll since instance is not running anymore
    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(currentInstanceStore.state.instance).toEqual(
        finishedCurrentInstanceMock
      )
    );

    jest.useRealTimers();
  });

  it('should set current instance', async () => {
    expect(currentInstanceStore.state.instance).toEqual(null);
    currentInstanceStore.setCurrentInstance({id: '123', state: 'ACTIVE'});
    expect(currentInstanceStore.state.instance).toEqual({
      id: '123',
      state: 'ACTIVE',
    });
  });

  it('should get workflow title', async () => {
    expect(currentInstanceStore.workflowTitle).toBe(null);
    currentInstanceStore.setCurrentInstance({
      id: '123',
      state: 'ACTIVE',
      workflowName: 'workflowName',
    });
    expect(currentInstanceStore.workflowTitle).toBe(
      'Camunda Operate: Instance 123 of Workflow workflowName'
    );
  });

  it('should reset store', async () => {
    expect(currentInstanceStore.workflowTitle).toBe(null);
    currentInstanceStore.setCurrentInstance({
      id: '123',
      state: 'ACTIVE',
      workflowName: 'workflowName',
    });
    expect(currentInstanceStore.state.instance).toEqual({
      id: '123',
      state: 'ACTIVE',
      workflowName: 'workflowName',
    });
    currentInstanceStore.reset();
    expect(currentInstanceStore.workflowTitle).toBe(null);
  });
});
