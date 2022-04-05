/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {singleInstanceDiagramStore} from './singleInstanceDiagram';
import {currentInstanceStore} from './currentInstance';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {
  createInstance,
  mockProcessXML,
  mockCallActivityProcessXML,
} from 'modules/testUtils';
import {waitFor} from '@testing-library/react';

describe('stores/singleInstanceDiagram', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );
  });

  afterEach(() => {
    singleInstanceDiagramStore.reset();
    currentInstanceStore.reset();
  });

  it('should fetch process xml when current instance is available', async () => {
    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processId: '10',
      })
    );

    singleInstanceDiagramStore.init();

    expect(singleInstanceDiagramStore.state.status).toBe('first-fetch');

    await waitFor(() => {
      expect(singleInstanceDiagramStore.state.status).toBe('fetched');
      expect(singleInstanceDiagramStore.state.diagramModel).not.toBeNull();
    });
  });

  it('should handle diagram fetch', async () => {
    expect(singleInstanceDiagramStore.state.status).toBe('initial');
    singleInstanceDiagramStore.fetchProcessXml('1');
    expect(singleInstanceDiagramStore.state.status).toBe('first-fetch');

    await waitFor(() =>
      expect(singleInstanceDiagramStore.state.status).toBe('fetched')
    );

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    singleInstanceDiagramStore.fetchProcessXml('1');
    expect(singleInstanceDiagramStore.state.status).toBe('fetching');

    await waitFor(() =>
      expect(singleInstanceDiagramStore.state.status).toBe('fetched')
    );
  });

  it('should get metaData', async () => {
    await singleInstanceDiagramStore.fetchProcessXml('1');

    expect(
      singleInstanceDiagramStore.getMetaData('invalid_activity_id')
    ).toEqual(undefined);

    expect(singleInstanceDiagramStore.getMetaData('StartEvent_1')).toEqual({
      name: undefined,
      type: {
        elementType: 'START',
        eventType: undefined,
        multiInstanceType: undefined,
      },
    });

    expect(
      singleInstanceDiagramStore.getMetaData('ServiceTask_0kt6c5i')
    ).toEqual({
      name: undefined,
      type: {
        elementType: 'TASK_SERVICE',
        eventType: undefined,
        multiInstanceType: undefined,
      },
    });

    expect(singleInstanceDiagramStore.getMetaData('EndEvent_0crvjrk')).toEqual({
      name: undefined,
      type: {
        elementType: 'END',
        eventType: undefined,
        multiInstanceType: undefined,
      },
    });
  });

  it('should get areDiagramDefinitionsAvailable', async () => {
    expect(singleInstanceDiagramStore.areDiagramDefinitionsAvailable).toBe(
      false
    );

    await singleInstanceDiagramStore.fetchProcessXml('1');

    expect(singleInstanceDiagramStore.areDiagramDefinitionsAvailable).toBe(
      true
    );
  });

  it('should reset store', async () => {
    await singleInstanceDiagramStore.fetchProcessXml('1');

    expect(singleInstanceDiagramStore.state.status).toBe('fetched');
    expect(singleInstanceDiagramStore.state.diagramModel).not.toEqual(null);

    singleInstanceDiagramStore.reset();

    expect(singleInstanceDiagramStore.state.status).toBe('initial');
    expect(singleInstanceDiagramStore.state.diagramModel).toEqual(null);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    currentInstanceStore.setCurrentInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processId: '10',
      })
    );
    singleInstanceDiagramStore.init();

    await waitFor(() =>
      expect(singleInstanceDiagramStore.state.status).toEqual('fetched')
    );

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    eventListeners.online();

    expect(singleInstanceDiagramStore.state.status).toEqual('fetching');

    await waitFor(() =>
      expect(singleInstanceDiagramStore.state.status).toEqual('fetched')
    );

    window.addEventListener = originalEventListener;
  });

  describe('hasCalledInstances', () => {
    it('should return true for processes with call activity', async () => {
      mockServer.use(
        rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
          res.once(ctx.text(mockCallActivityProcessXML))
        )
      );

      currentInstanceStore.setCurrentInstance(
        createInstance({
          id: '123',
          state: 'ACTIVE',
          processId: '10',
        })
      );

      singleInstanceDiagramStore.init();

      await waitFor(() =>
        expect(singleInstanceDiagramStore.state.status).toEqual('fetched')
      );

      expect(singleInstanceDiagramStore.hasCalledInstances).toBe(true);
    });

    it('should return false for processes without call activity', async () => {
      mockServer.use(
        rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
          res.once(ctx.text(mockProcessXML))
        )
      );

      currentInstanceStore.setCurrentInstance(
        createInstance({
          id: '123',
          state: 'ACTIVE',
          processId: '10',
        })
      );

      singleInstanceDiagramStore.init();

      await waitFor(() =>
        expect(singleInstanceDiagramStore.state.status).toEqual('fetched')
      );

      expect(singleInstanceDiagramStore.hasCalledInstances).toBe(false);
    });
  });
});
