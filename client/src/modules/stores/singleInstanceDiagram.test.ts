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
    currentInstanceStore.setCurrentInstance({id: 123, state: 'ACTIVE'});

    singleInstanceDiagramStore.init();

    expect(singleInstanceDiagramStore.state.isLoading).toBe(true);
    expect(singleInstanceDiagramStore.state.isInitialLoadComplete).toBe(false);

    await waitFor(() => {
      expect(singleInstanceDiagramStore.state.isLoading).toBe(false);
      expect(singleInstanceDiagramStore.state.isInitialLoadComplete).toBe(true);
      expect(singleInstanceDiagramStore.state.diagramModel).not.toBeNull();
    });
  });

  it('should start loading', async () => {
    expect(singleInstanceDiagramStore.state.isLoading).toBe(false);
    singleInstanceDiagramStore.startLoading();
    expect(singleInstanceDiagramStore.state.isLoading).toBe(true);
  });

  it('should complete initial load', async () => {
    expect(singleInstanceDiagramStore.state.isInitialLoadComplete).toBe(false);
    singleInstanceDiagramStore.completeInitialLoad();
    expect(singleInstanceDiagramStore.state.isInitialLoadComplete).toBe(true);
  });

  it('should get metaData', async () => {
    await singleInstanceDiagramStore.fetchWorkflowXml(1);

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

    await singleInstanceDiagramStore.fetchWorkflowXml(1);

    expect(singleInstanceDiagramStore.areDiagramDefinitionsAvailable).toBe(
      true
    );
  });

  it('should reset store', async () => {
    await singleInstanceDiagramStore.fetchWorkflowXml(1);

    expect(singleInstanceDiagramStore.state.isInitialLoadComplete).toBe(true);
    expect(singleInstanceDiagramStore.state.diagramModel).not.toEqual(null);

    singleInstanceDiagramStore.reset();

    expect(singleInstanceDiagramStore.state.isInitialLoadComplete).toBe(false);
    expect(singleInstanceDiagramStore.state.diagramModel).toEqual(null);
  });
});
