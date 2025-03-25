/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {useProcessDefinitionXml} from './useProcessDefinitionXml';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
jest.mock('modules/hooks/useProcessInstancesFilters');

describe('useProcessDefinitionXml', () => {
  const wrapper = ({children}: {children: React.ReactNode}) => {
    return (
      <QueryClientProvider client={getMockQueryClient()}>
        {children}
      </QueryClientProvider>
    );
  };

  it('should fetch and parse process definition xml successfully', async () => {
    const mockXml = `<?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_0lflhvc" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.31.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.6.0">
        <bpmn:process id="Process_1uk8sj2" isExecutable="true">
          <bpmn:startEvent id="StartEvent_1">
            <bpmn:outgoing>Flow_1mg9dg7</bpmn:outgoing>
          </bpmn:startEvent>
          <bpmn:task id="TestTask" name="Test Task">
            <bpmn:incoming>Flow_1mg9dg7</bpmn:incoming>
            <bpmn:outgoing>Flow_0wd3yi3</bpmn:outgoing>
          </bpmn:task>
          <bpmn:sequenceFlow id="Flow_1mg9dg7" sourceRef="StartEvent_1" targetRef="TestTask" />
          <bpmn:endEvent id="Event_1s98q4d">
            <bpmn:incoming>Flow_0wd3yi3</bpmn:incoming>
          </bpmn:endEvent>
          <bpmn:sequenceFlow id="Flow_0wd3yi3" sourceRef="TestTask" targetRef="Event_1s98q4d" />
        </bpmn:process>
        <bpmndi:BPMNDiagram id="BPMNDiagram_1">
          <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1uk8sj2">
            <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
              <dc:Bounds x="182" y="102" width="36" height="36" />
            </bpmndi:BPMNShape>
            <bpmndi:BPMNShape id="Activity_01d6koh_di" bpmnElement="TestTask">
              <dc:Bounds x="270" y="80" width="100" height="80" />
              <bpmndi:BPMNLabel />
            </bpmndi:BPMNShape>
            <bpmndi:BPMNShape id="Event_1s98q4d_di" bpmnElement="Event_1s98q4d">
              <dc:Bounds x="422" y="102" width="36" height="36" />
            </bpmndi:BPMNShape>
            <bpmndi:BPMNEdge id="Flow_1mg9dg7_di" bpmnElement="Flow_1mg9dg7">
              <di:waypoint x="218" y="120" />
              <di:waypoint x="270" y="120" />
            </bpmndi:BPMNEdge>
            <bpmndi:BPMNEdge id="Flow_0wd3yi3_di" bpmnElement="Flow_0wd3yi3">
              <di:waypoint x="370" y="120" />
              <di:waypoint x="422" y="120" />
            </bpmndi:BPMNEdge>
          </bpmndi:BPMNPlane>
        </bpmndi:BPMNDiagram>
      </bpmn:definitions>`;

    mockFetchProcessDefinitionXml().withSuccess(mockXml);

    const {result} = renderHook(
      () =>
        useProcessDefinitionXml({
          processDefinitionKey: '27589024892748902347',
          enabled: true,
        }),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.data).resolves.not.toBeNull());

    const resolvedData = await result.current.data;

    expect(resolvedData?.xml).toBe(mockXml);
    expect(resolvedData?.diagramModel.elementsById['TestTask']).toEqual({
      $type: 'bpmn:Task',
      id: 'TestTask',
      name: 'Test Task',
    });
    expect(resolvedData?.selectableFlowNodes).toEqual([
      {
        $type: 'bpmn:StartEvent',
        id: 'StartEvent_1',
      },
      {
        $type: 'bpmn:Task',
        id: 'TestTask',
        name: 'Test Task',
      },
      {
        $type: 'bpmn:EndEvent',
        id: 'Event_1s98q4d',
      },
    ]);
  });
});
