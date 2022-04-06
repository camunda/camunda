/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {
  createInstance,
  mockProcessXML,
  mockCallActivityProcessXML,
} from 'modules/testUtils';
import {waitFor} from '@testing-library/react';

describe('stores/processInstanceDiagram', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );
  });

  afterEach(() => {
    processInstanceDetailsDiagramStore.reset();
    processInstanceDetailsStore.reset();
  });

  it('should fetch process xml when current instance is available', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processId: '10',
      })
    );

    processInstanceDetailsDiagramStore.init();

    expect(processInstanceDetailsDiagramStore.state.status).toBe('first-fetch');

    await waitFor(() => {
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched');
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull();
    });
  });

  it('should handle diagram fetch', async () => {
    expect(processInstanceDetailsDiagramStore.state.status).toBe('initial');
    processInstanceDetailsDiagramStore.fetchProcessXml('1');
    expect(processInstanceDetailsDiagramStore.state.status).toBe('first-fetch');

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched')
    );

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    processInstanceDetailsDiagramStore.fetchProcessXml('1');
    expect(processInstanceDetailsDiagramStore.state.status).toBe('fetching');

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched')
    );
  });

  it('should get metaData', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(
      processInstanceDetailsDiagramStore.getMetaData('invalid_activity_id')
    ).toEqual(undefined);

    expect(
      processInstanceDetailsDiagramStore.getMetaData('StartEvent_1')
    ).toEqual({
      name: undefined,
      type: {
        elementType: 'START',
        eventType: undefined,
        multiInstanceType: undefined,
      },
    });

    expect(
      processInstanceDetailsDiagramStore.getMetaData('ServiceTask_0kt6c5i')
    ).toEqual({
      name: undefined,
      type: {
        elementType: 'TASK_SERVICE',
        eventType: undefined,
        multiInstanceType: undefined,
      },
    });

    expect(
      processInstanceDetailsDiagramStore.getMetaData('EndEvent_0crvjrk')
    ).toEqual({
      name: undefined,
      type: {
        elementType: 'END',
        eventType: undefined,
        multiInstanceType: undefined,
      },
    });
  });

  it('should get areDiagramDefinitionsAvailable', async () => {
    expect(
      processInstanceDetailsDiagramStore.areDiagramDefinitionsAvailable
    ).toBe(false);

    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(
      processInstanceDetailsDiagramStore.areDiagramDefinitionsAvailable
    ).toBe(true);
  });

  it('should reset store', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched');
    expect(processInstanceDetailsDiagramStore.state.diagramModel).not.toEqual(
      null
    );

    processInstanceDetailsDiagramStore.reset();

    expect(processInstanceDetailsDiagramStore.state.status).toBe('initial');
    expect(processInstanceDetailsDiagramStore.state.diagramModel).toEqual(null);
  });

  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processId: '10',
      })
    );
    processInstanceDetailsDiagramStore.init();

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toEqual('fetched')
    );

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessXML))
      )
    );

    eventListeners.online();

    expect(processInstanceDetailsDiagramStore.state.status).toEqual('fetching');

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toEqual('fetched')
    );

    window.addEventListener = originalEventListener;
  });

  describe('hasCalledProcessInstances', () => {
    it('should return true for processes with call activity', async () => {
      mockServer.use(
        rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
          res.once(ctx.text(mockCallActivityProcessXML))
        )
      );

      processInstanceDetailsStore.setProcessInstance(
        createInstance({
          id: '123',
          state: 'ACTIVE',
          processId: '10',
        })
      );

      processInstanceDetailsDiagramStore.init();

      await waitFor(() =>
        expect(processInstanceDetailsDiagramStore.state.status).toEqual(
          'fetched'
        )
      );

      expect(processInstanceDetailsDiagramStore.hasCalledProcessInstances).toBe(
        true
      );
    });

    it('should return false for processes without call activity', async () => {
      mockServer.use(
        rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
          res.once(ctx.text(mockProcessXML))
        )
      );

      processInstanceDetailsStore.setProcessInstance(
        createInstance({
          id: '123',
          state: 'ACTIVE',
          processId: '10',
        })
      );

      processInstanceDetailsDiagramStore.init();

      await waitFor(() =>
        expect(processInstanceDetailsDiagramStore.state.status).toEqual(
          'fetched'
        )
      );

      expect(processInstanceDetailsDiagramStore.hasCalledProcessInstances).toBe(
        false
      );
    });
  });
});
