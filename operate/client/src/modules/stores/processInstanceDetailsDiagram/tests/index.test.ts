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
import {modificationsStore} from 'modules/stores/modifications';
import {mockNestedSubprocess} from 'modules/mocks/mockNestedSubprocess';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {open} from 'modules/mocks/diagrams';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

describe('stores/processInstanceDiagram', () => {
  beforeEach(() => {
    mockFetchProcessXML().withSuccess(mockProcessXML);
  });

  afterEach(() => {
    processInstanceDetailsDiagramStore.reset();
    processInstanceDetailsStore.reset();
    processInstanceDetailsStatisticsStore.reset();
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

  it('should get modifiable-nonmodifiable flow nodes', async () => {
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'StartEvent_1',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'service-task-1',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'multi-instance-subprocess',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
      {
        activityId: 'subprocess-start-1',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'subprocess-service-task',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
      {
        activityId: 'service-task-7',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        activityId: 'message-boundary',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ]);

    mockFetchProcessXML().withSuccess(open('diagramForModifications.bpmn'));

    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId',
    );
    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      'processInstanceId',
    );

    expect(processInstanceDetailsDiagramStore.appendableFlowNodes).toEqual([
      'service-task-1',
      'multi-instance-subprocess',
      'gateway-1',
      'gateway-2',
      'Event_1o1ply5',
      'service-task-7',
      'message-intermediate',
      'timer-intermediate',
      'user-task-1',
      'end-event',
      'service-task-2',
      'service-task-3',
      'service-task-4',
      'service-task-5',
      'service-task-6',
      'intermediate-throw',
      'multi-instance-service-task',
      'Gateway_0uhrn1w',
      'Gateway_1qjpson',
    ]);

    expect(processInstanceDetailsDiagramStore.cancellableFlowNodes).toEqual([
      'multi-instance-subprocess',
      'subprocess-service-task',
      'message-boundary',
      'service-task-7',
    ]);

    expect(processInstanceDetailsDiagramStore.modifiableFlowNodes).toEqual([
      'service-task-1',
      'multi-instance-subprocess',
      'gateway-1',
      'gateway-2',
      'Event_1o1ply5',
      'service-task-7',
      'message-intermediate',
      'timer-intermediate',
      'user-task-1',
      'end-event',
      'service-task-2',
      'service-task-3',
      'service-task-4',
      'service-task-5',
      'service-task-6',
      'intermediate-throw',
      'multi-instance-service-task',
      'Gateway_0uhrn1w',
      'Gateway_1qjpson',
      'subprocess-service-task',
      'message-boundary',
    ]);

    expect(processInstanceDetailsDiagramStore.nonModifiableFlowNodes).toEqual([
      'StartEvent_1',
      'subprocess-start-1',
      'subprocess-end-task',
      'error-boundary',
      'non-interrupt-timer-boundary',
      'non-interrupt-message-boundary',
      'timer-boundary',
      'boundary-event',
      'eventAttachedToEventbasedGateway1',
      'eventAttachedToEventbasedGateway2',
    ]);

    modificationsStore.startMovingToken('service-task-1');

    expect(processInstanceDetailsDiagramStore.nonModifiableFlowNodes).toEqual([
      'StartEvent_1',
      'service-task-1',
      'subprocess-start-1',
      'subprocess-end-task',
      'subprocess-service-task',
      'message-boundary',
      'error-boundary',
      'non-interrupt-timer-boundary',
      'non-interrupt-message-boundary',
      'timer-boundary',
      'boundary-event',
      'eventAttachedToEventbasedGateway1',
      'eventAttachedToEventbasedGateway2',
    ]);
  });

  it('should get flow node parents', async () => {
    mockFetchProcessXML().withSuccess(mockNestedSubprocess);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processId: '10',
        bpmnProcessId: 'nested_sub_process',
      }),
    );

    processInstanceDetailsDiagramStore.init();

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toEqual(
        'fetched',
      ),
    );

    expect(
      processInstanceDetailsDiagramStore.getFlowNodeParents('user_task'),
    ).toEqual(['inner_sub_process', 'parent_sub_process']);

    expect(
      processInstanceDetailsDiagramStore.getFlowNodeParents(
        'inner_sub_process',
      ),
    ).toEqual(['parent_sub_process']);

    expect(
      processInstanceDetailsDiagramStore.getFlowNodeParents(
        'parent_sub_process',
      ),
    ).toEqual([]);

    expect(
      processInstanceDetailsDiagramStore.getFlowNodeParents('non_existing'),
    ).toEqual([]);
  });

  it('should get flow nodes in between two flow nodes', async () => {
    mockFetchProcessXML().withSuccess(mockNestedSubprocess);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processId: '10',
        bpmnProcessId: 'nested_sub_process',
      }),
    );

    processInstanceDetailsDiagramStore.init();

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toEqual(
        'fetched',
      ),
    );

    expect(
      processInstanceDetailsDiagramStore.getFlowNodesInBetween(
        'user_task',
        'nested_sub_process',
      ),
    ).toEqual(['inner_sub_process', 'parent_sub_process']);

    expect(
      processInstanceDetailsDiagramStore.getFlowNodesInBetween(
        'inner_sub_process',
        'nested_sub_process',
      ),
    ).toEqual(['parent_sub_process']);

    expect(
      processInstanceDetailsDiagramStore.getFlowNodesInBetween(
        'parent_sub_process',
        'nested_sub_process',
      ),
    ).toEqual([]);

    expect(
      processInstanceDetailsDiagramStore.getFlowNodesInBetween(
        'user_task',
        'parent_sub_process',
      ),
    ).toEqual(['inner_sub_process']);

    expect(
      processInstanceDetailsDiagramStore.getFlowNodesInBetween(
        'inner_sub_process',
        'parent_sub_process',
      ),
    ).toEqual([]);

    expect(
      processInstanceDetailsDiagramStore.getFlowNodesInBetween(
        'user_task',
        'inner_sub_process',
      ),
    ).toEqual([]);
  });

  it('should get has multiple scopes', async () => {
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'service-task-1',
        active: 2,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'service-task-7',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        activityId: 'subprocess-service-task',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        activityId: 'multi-instance-subprocess',
        active: 2,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ]);

    mockFetchProcessXML().withSuccess(open('diagramForModifications.bpmn'));

    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId',
    );
    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      'processInstanceId',
    );

    expect(processInstanceDetailsDiagramStore.hasMultipleScopes()).toBe(false);

    expect(
      processInstanceDetailsDiagramStore.hasMultipleScopes({
        id: 'service-task-1',
        name: 'some-name',
        $type: 'bpmn:ServiceTask',
      }),
    ).toBe(true);

    expect(
      processInstanceDetailsDiagramStore.hasMultipleScopes({
        id: 'service-task-7',
        name: 'some-name',
        $type: 'bpmn:ServiceTask',
      }),
    ).toBe(false);

    expect(
      processInstanceDetailsDiagramStore.hasMultipleScopes({
        id: 'subprocess-service-task',
        name: 'some-name',
        $type: 'bpmn:ServiceTask',
      }),
    ).toBe(false);

    expect(
      processInstanceDetailsDiagramStore.hasMultipleScopes({
        id: 'subprocess-service-task',
        name: 'some-name',
        $type: 'bpmn:ServiceTask',
        $parent: {
          id: 'multi-instance-subprocess',
          name: 'some-name',
          $type: 'bpmn:SubProcess',
        },
      }),
    ).toBe(true);
  });

  it('should get parent flow node', async () => {
    mockFetchProcessXML().withSuccess(open('diagramForModifications.bpmn'));

    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId',
    );

    expect(
      processInstanceDetailsDiagramStore.getParentFlowNode(
        'subprocess-service-task',
      ),
    ).toEqual({
      $type: 'bpmn:SubProcess',
      flowElements: [
        {$type: 'bpmn:StartEvent', id: 'subprocess-start-1'},
        {$type: 'bpmn:SequenceFlow', id: 'Flow_1vur7mf'},
        {$type: 'bpmn:EndEvent', id: 'subprocess-end-task'},
        {$type: 'bpmn:SequenceFlow', id: 'Flow_0r3hsrs'},
        {$type: 'bpmn:ServiceTask', id: 'subprocess-service-task'},
      ],
      id: 'multi-instance-subprocess',
      loopCharacteristics: {$type: 'bpmn:MultiInstanceLoopCharacteristics'},
    });
  });
});
