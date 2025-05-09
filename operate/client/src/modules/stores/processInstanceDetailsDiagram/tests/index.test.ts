/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {createInstance, mockProcessXML} from 'modules/testUtils';
import {waitFor} from 'modules/testing-library';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

describe('stores/processInstanceDiagram', () => {
  beforeEach(() => {
    mockFetchProcessXML().withSuccess(mockProcessXML);
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
      }),
    );

    processInstanceDetailsDiagramStore.init();

    expect(processInstanceDetailsDiagramStore.state.status).toBe('first-fetch');

    await waitFor(() => {
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched');
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel,
      ).not.toBeNull();
    });
  });

  it('should handle diagram fetch', async () => {
    expect(processInstanceDetailsDiagramStore.state.status).toBe('initial');
    processInstanceDetailsDiagramStore.fetchProcessXml('1');
    expect(processInstanceDetailsDiagramStore.state.status).toBe('first-fetch');

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched'),
    );

    mockFetchProcessXML().withSuccess(mockProcessXML);

    processInstanceDetailsDiagramStore.fetchProcessXml('1');
    expect(processInstanceDetailsDiagramStore.state.status).toBe('fetching');

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched'),
    );
  });

  it('should get business object', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(
      processInstanceDetailsDiagramStore.businessObjects['invalid_activity_id'],
    ).toEqual(undefined);

    expect(
      processInstanceDetailsDiagramStore.businessObjects['StartEvent_1'],
    ).toEqual({
      $type: 'bpmn:StartEvent',
      id: 'StartEvent_1',
      name: 'Start Event 1',
    });

    expect(
      processInstanceDetailsDiagramStore.businessObjects['ServiceTask_0kt6c5i'],
    ).toEqual({
      $type: 'bpmn:ServiceTask',
      extensionElements: {
        $type: 'bpmn:ExtensionElements',
        values: [
          {
            $type: 'zeebe:taskDefinition',
            type: 'task',
          },
        ],
      },
      id: 'ServiceTask_0kt6c5i',
      name: 'Service Task 1',
    });

    expect(
      processInstanceDetailsDiagramStore.businessObjects['EndEvent_0crvjrk'],
    ).toEqual({
      $type: 'bpmn:EndEvent',
      id: 'EndEvent_0crvjrk',
      name: 'End Event',
    });
  });

  it('should reset store', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml('1');

    expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched');
    expect(processInstanceDetailsDiagramStore.state.diagramModel).not.toEqual(
      null,
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
      }),
    );
    processInstanceDetailsDiagramStore.init();

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toEqual(
        'fetched',
      ),
    );

    mockFetchProcessXML().withSuccess(mockProcessXML);

    eventListeners.online();

    expect(processInstanceDetailsDiagramStore.state.status).toEqual('fetching');

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toEqual(
        'fetched',
      ),
    );

    window.addEventListener = originalEventListener;
  });
});
