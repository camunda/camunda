/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {currentInstanceStore} from './currentInstance';
import {createInstance} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';

const currentInstanceMock = createInstance();

describe('stores/currentInstance', () => {
  afterEach(() => {
    currentInstanceStore.reset();
  });

  beforeEach(() => {
    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(currentInstanceMock))
      )
    );
  });

  it('should fetch current instance on init state', async () => {
    currentInstanceStore.init('1');
    await waitFor(() =>
      expect(currentInstanceStore.state.instance).toEqual(currentInstanceMock)
    );
  });

  it('should poll if current instance is running', async () => {
    jest.useFakeTimers();
    currentInstanceStore.init('1');
    await waitFor(() =>
      expect(currentInstanceStore.state.instance).toEqual(currentInstanceMock)
    );

    const secondCurrentInstanceMock = createInstance();

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
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
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
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
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
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

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should set current instance', async () => {
    const mockInstance = createInstance({id: '123', state: 'ACTIVE'});
    expect(currentInstanceStore.state.instance).toEqual(null);
    currentInstanceStore.setCurrentInstance(
      createInstance({id: '123', state: 'ACTIVE'})
    );
    expect(currentInstanceStore.state.instance).toEqual(mockInstance);
  });

  it('should get process title', async () => {
    expect(currentInstanceStore.processTitle).toBe(null);
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processName: 'processName',
      })
    );
    expect(currentInstanceStore.processTitle).toBe(
      'Camunda Operate: Instance 123 of Process processName'
    );
  });

  it('should reset store', async () => {
    const mockInstance = createInstance({
      id: '123',
      state: 'ACTIVE',
      processName: 'processName',
    });

    expect(currentInstanceStore.processTitle).toBe(null);
    currentInstanceStore.setCurrentInstance(mockInstance);
    expect(currentInstanceStore.state.instance).toEqual(mockInstance);
    currentInstanceStore.reset();
    expect(currentInstanceStore.processTitle).toBe(null);
  });

  it('should set active operation state', async () => {
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: '123',
        hasActiveOperation: false,
      })
    );

    expect(currentInstanceStore.state.instance?.hasActiveOperation).toBe(false);
    currentInstanceStore.activateOperation();
    expect(currentInstanceStore.state.instance?.hasActiveOperation).toBe(true);
    currentInstanceStore.deactivateOperation();
    expect(currentInstanceStore.state.instance?.hasActiveOperation).toBe(false);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    currentInstanceStore.init('1');

    await waitFor(() =>
      expect(currentInstanceStore.state.instance).toEqual(currentInstanceMock)
    );

    mockServer.use(
      rest.get('/api/process-instances/:id', (_, res, ctx) =>
        res.once(ctx.json({...currentInstanceMock, state: 'INCIDENT'}))
      )
    );

    eventListeners.online();

    await waitFor(() =>
      expect(currentInstanceStore.state.instance).toEqual({
        ...currentInstanceMock,
        state: 'INCIDENT',
      })
    );

    window.addEventListener = originalEventListener;
  });
});
