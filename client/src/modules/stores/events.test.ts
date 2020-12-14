/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {eventsStore} from './events';
import {currentInstanceStore} from './currentInstance';
import {createInstance, createEvent} from 'modules/testUtils';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {waitFor} from '@testing-library/react';

const currentInstanceMock = createInstance();
const eventsMock = [createEvent({id: 1}), createEvent({id: 2})];

describe('stores/events', () => {
  afterEach(() => {
    eventsStore.reset();
    currentInstanceStore.reset();
  });

  beforeEach(() => {
    mockServer.use(
      rest.post('/api/events', (_, res, ctx) => res.once(ctx.json(eventsMock)))
    );
  });

  it('should fetch events when current instance is available', async () => {
    eventsStore.init();
    currentInstanceStore.setCurrentInstance({id: 1, state: 'CANCELED'});

    await waitFor(() => expect(eventsStore.state.items).toEqual(eventsMock));
  });

  it('should poll if current instance is running', async () => {
    jest.useFakeTimers();
    currentInstanceStore.setCurrentInstance(currentInstanceMock);
    eventsStore.init();

    await waitFor(() => expect(eventsStore.state.items).toEqual(eventsMock));

    const secondEventsMock = [createEvent({id: 3}), createEvent({id: 4})];

    mockServer.use(
      rest.post('/api/events', (_, res, ctx) =>
        res.once(ctx.json(secondEventsMock))
      )
    );

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(eventsStore.state.items).toEqual(secondEventsMock);
    });

    currentInstanceStore.setCurrentInstance(
      createInstance({state: 'CANCELED'})
    );

    jest.runOnlyPendingTimers();
    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(eventsStore.state.items).toEqual(secondEventsMock);
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should set items', async () => {
    expect(eventsStore.state.items).toEqual([]);
    eventsStore.setItems(eventsMock);
    expect(eventsStore.state.items).toEqual(eventsMock);
  });

  it('should reset store', async () => {
    await eventsStore.fetchWorkflowEvents('1');
    expect(eventsStore.state.items).toEqual(eventsMock);
    eventsStore.reset();
    expect(eventsStore.state.items).toEqual([]);
  });
});
