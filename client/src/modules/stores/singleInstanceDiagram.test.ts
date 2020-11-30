/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {singleInstanceDiagramStore} from './singleInstanceDiagram';
import {currentInstanceStore} from './currentInstance';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {mockWorkflowXML} from 'modules/testUtils';
import {waitFor} from '@testing-library/react';

describe('stores/singleInstanceDiagram', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockWorkflowXML))
      )
    );
  });

  afterEach(() => {
    singleInstanceDiagramStore.reset();
    currentInstanceStore.reset();
  });

  it('should fetch workflow xml when current instance is available', async () => {
    currentInstanceStore.setCurrentInstance({
      id: 123,
      state: 'ACTIVE',
      workflowId: '10',
    });

    singleInstanceDiagramStore.init();

    expect(singleInstanceDiagramStore.state.status).toBe('first-fetch');

    await waitFor(() => {
      expect(singleInstanceDiagramStore.state.status).toBe('fetched');
      expect(singleInstanceDiagramStore.state.diagramModel).not.toBeNull();
    });
  });

  it('should handle diagram fetch', async () => {
    expect(singleInstanceDiagramStore.state.status).toBe('initial');
    singleInstanceDiagramStore.fetchWorkflowXml('1');
    expect(singleInstanceDiagramStore.state.status).toBe('first-fetch');

    await waitFor(() =>
      expect(singleInstanceDiagramStore.state.status).toBe('fetched')
    );

    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockWorkflowXML))
      )
    );

    singleInstanceDiagramStore.fetchWorkflowXml('1');
    expect(singleInstanceDiagramStore.state.status).toBe('fetching');

    await waitFor(() =>
      expect(singleInstanceDiagramStore.state.status).toBe('fetched')
    );
  });

  it('should get metaData', async () => {
    await singleInstanceDiagramStore.fetchWorkflowXml('1');

    expect(
      singleInstanceDiagramStore.getMetaData('invalid_activity_id')
    ).toEqual(undefined);

    expect(singleInstanceDiagramStore.getMetaData('StartEvent_1')).toEqual({
      name: undefined,
      type: {
        elementType: 'START',
        eventType: false,
        multiInstanceType: undefined,
      },
    });

    expect(
      singleInstanceDiagramStore.getMetaData('ServiceTask_0kt6c5i')
    ).toEqual({
      name: undefined,
      type: {
        elementType: 'TASK_SERVICE',
        eventType: false,
        multiInstanceType: undefined,
      },
    });

    expect(singleInstanceDiagramStore.getMetaData('EndEvent_0crvjrk')).toEqual({
      name: undefined,
      type: {
        elementType: 'END',
        eventType: false,
        multiInstanceType: undefined,
      },
    });
  });

  it('should get areDiagramDefinitionsAvailable', async () => {
    expect(singleInstanceDiagramStore.areDiagramDefinitionsAvailable).toBe(
      false
    );

    await singleInstanceDiagramStore.fetchWorkflowXml('1');

    expect(singleInstanceDiagramStore.areDiagramDefinitionsAvailable).toBe(
      true
    );
  });

  it('should reset store', async () => {
    await singleInstanceDiagramStore.fetchWorkflowXml('1');

    expect(singleInstanceDiagramStore.state.status).toBe('fetched');
    expect(singleInstanceDiagramStore.state.diagramModel).not.toEqual(null);

    singleInstanceDiagramStore.reset();

    expect(singleInstanceDiagramStore.state.status).toBe('initial');
    expect(singleInstanceDiagramStore.state.diagramModel).toEqual(null);
  });
});
