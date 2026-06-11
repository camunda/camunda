/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {InstanceMock} from '.';

const documentReferenceProcessInstance: InstanceMock = {
  detail: {
    processDefinitionId: 'view_documents_process',
    processDefinitionName: 'View Documents',
    processDefinitionVersion: 1,
    processDefinitionVersionTag: null,
    startDate: '2026-06-08T12:47:13.885Z',
    endDate: null,
    state: 'ACTIVE',
    hasIncident: false,
    tenantId: '<default>',
    processInstanceKey: '2251799813790427',
    processDefinitionKey: '2251799813790414',
    parentProcessInstanceKey: null,
    parentElementInstanceKey: null,
    rootProcessInstanceKey: '2251799813790427',
    tags: [],
    businessId: null,
  },
  statistics: {
    items: [
      {
        elementId: 'start_event',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'view_documents_task',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
    ],
  },
  callHierarchy: [],
  sequenceFlows: {
    items: [
      {
        sequenceFlowId: '2251799813790427_start_task',
        processInstanceKey: '2251799813790427',
        processDefinitionKey: '2251799813790414',
        processDefinitionId: 'view_documents_process',
        elementId: 'start_task',
        tenantId: '<default>',
      },
    ],
  },
  elementInstances: {
    items: [
      {
        processDefinitionId: 'view_documents_process',
        startDate: '2026-06-08T12:47:13.885Z',
        endDate: '2026-06-08T12:47:13.885Z',
        elementId: 'start_event',
        elementName: 'start_event',
        type: 'START_EVENT',
        state: 'COMPLETED',
        hasIncident: false,
        tenantId: '<default>',
        elementInstanceKey: '2251799813790429',
        processInstanceKey: '2251799813790427',
        rootProcessInstanceKey: '2251799813790427',
        processDefinitionKey: '2251799813790414',
        incidentKey: null,
      },
      {
        processDefinitionId: 'view_documents_process',
        startDate: '2026-06-08T12:47:13.885Z',
        endDate: null,
        elementId: 'view_documents_task',
        elementName: 'View Documents',
        type: 'USER_TASK',
        state: 'ACTIVE',
        hasIncident: false,
        tenantId: '<default>',
        elementInstanceKey: '2251799813790431',
        processInstanceKey: '2251799813790427',
        rootProcessInstanceKey: '2251799813790427',
        processDefinitionKey: '2251799813790414',
        incidentKey: null,
      },
    ],
    page: {
      totalItems: 2,
      hasMoreTotalItems: false,
      startCursor: 'WzE3ODA5MjI4MzM4ODUsMjI1MTc5OTgxMzc5MDQyOV0=',
      endCursor: 'WzE3ODA5MjI4MzM4ODUsMjI1MTc5OTgxMzc5MDQzMV0=',
    },
  },
  xml: `<?xml version="1.0" encoding="UTF-8"?>
  <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_15fdfy7" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.47.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.10.0">
    <bpmn:process id="view_documents_process" name="View Documents" isExecutable="true">
      <bpmn:startEvent id="start_event">
        <bpmn:outgoing>start_task</bpmn:outgoing>
      </bpmn:startEvent>
      <bpmn:userTask id="view_documents_task" name="View Documents">
        <bpmn:extensionElements>
          <zeebe:userTask />
        </bpmn:extensionElements>
        <bpmn:incoming>start_task</bpmn:incoming>
        <bpmn:outgoing>task_end</bpmn:outgoing>
      </bpmn:userTask>
      <bpmn:sequenceFlow id="start_task" sourceRef="start_event" targetRef="view_documents_task" />
      <bpmn:intermediateThrowEvent id="end_event">
        <bpmn:incoming>task_end</bpmn:incoming>
      </bpmn:intermediateThrowEvent>
      <bpmn:sequenceFlow id="task_end" sourceRef="view_documents_task" targetRef="end_event" />
    </bpmn:process>
    <bpmndi:BPMNDiagram id="BPMNDiagram_1">
      <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="view_documents_process">
        <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="start_event">
          <dc:Bounds x="182" y="102" width="36" height="36" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="Activity_0boto20_di" bpmnElement="view_documents_task">
          <dc:Bounds x="270" y="80" width="100" height="80" />
          <bpmndi:BPMNLabel />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNShape id="Event_10e9n81_di" bpmnElement="end_event">
          <dc:Bounds x="422" y="102" width="36" height="36" />
        </bpmndi:BPMNShape>
        <bpmndi:BPMNEdge id="Flow_0jetzki_di" bpmnElement="start_task">
          <di:waypoint x="218" y="120" />
          <di:waypoint x="270" y="120" />
        </bpmndi:BPMNEdge>
        <bpmndi:BPMNEdge id="Flow_07xs4nj_di" bpmnElement="task_end">
          <di:waypoint x="370" y="120" />
          <di:waypoint x="422" y="120" />
        </bpmndi:BPMNEdge>
      </bpmndi:BPMNPlane>
    </bpmndi:BPMNDiagram>
  </bpmn:definitions>
`,
  variables: [
    {
      value:
        '[{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"3f34f325-143e-4e25-9be2-56195c8fd07b","contentHash":"c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974","metadata":{"contentType":"application/json","fileName":"expired_json.json","expiresAt":"2026-06-01","size":187,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}}]',
      isTruncated: false,
      name: 'expired_doc',
      tenantId: '<default>',
      variableKey: '2251799813802006',
      scopeKey: '2251799813790427',
      processInstanceKey: '2251799813790427',
      rootProcessInstanceKey: '2251799813790427',
    },
    {
      value:
        '[{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"b6c19c47-e241-4e3a-9726-23aa7ca053ed","contentHash":"39920693ce70e3ac2dc85d8d2643afff54e153a874f18a30aa080fcabf85f572","metadata":{"contentType":"image/png","fileName":"test_image.png","expiresAt":null,"size":33906,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}}]',
      isTruncated: false,
      name: 'image_doc',
      tenantId: '<default>',
      variableKey: '2251799813790879',
      scopeKey: '2251799813790427',
      processInstanceKey: '2251799813790427',
      rootProcessInstanceKey: '2251799813790427',
    },
    {
      value:
        '[{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"3f34f325-143e-4e25-9be2-56195c8fd07b","contentHash":"c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974","metadata":{"contentType":"application/json","fileName":"test_json.json","expiresAt":null,"size":187,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}}]',
      isTruncated: false,
      name: 'json_doc',
      tenantId: '<default>',
      variableKey: '2251799813790883',
      scopeKey: '2251799813790427',
      processInstanceKey: '2251799813790427',
      rootProcessInstanceKey: '2251799813790427',
    },
    {
      value:
        '[{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"3f34f325-143e-4e25-9be2-56195c8fd07b","contentHash":"c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974","metadata":{"contentType":"application/json","fileName":"test_json.json","expiresAt":null,"size":187,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"ff787145-b82d-4ce4-bbc4-22c44f2201b8","contentHash":"848eaa0a3565d0bc72f5d82adc229b073fa971f65a78e1010879fe5ca7cbda96","metadata":{"contentType":"application/pdf","fileName":"test_document.pdf","expiresAt":null,"size":25894,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"b6c19c47-e241-4e3a-9726-23aa7ca053ed","contentHash":"39920693ce70e3ac2dc85d8d2643afff54e153a874f18a30aa080fcabf85f572","metadata":{"contentType":"image/png","fileName":"test_image.png","expiresAt":null,"size":33906,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"3f34f325-143e-4e25-9be2-56195c8fd07b","contentHash":"c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974","metadata":{"contentType":"application/json","fileName":"expired_json.json","expiresAt":"2026-06-01","size":187,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}}]',
      isTruncated: false,
      name: 'multiple_docs',
      tenantId: '<default>',
      variableKey: '2251799813790889',
      scopeKey: '2251799813790427',
      processInstanceKey: '2251799813790427',
      rootProcessInstanceKey: '2251799813790427',
    },
    {
      value:
        '[{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"ff787145-b82d-4ce4-bbc4-22c44f2201b8","contentHash":"848eaa0a3565d0bc72f5d82adc229b073fa971f65a78e1010879fe5ca7cbda96","metadata":{"contentType":"application/pdf","fileName":"test_document.pdf","expiresAt":null,"size":25894,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}}]',
      isTruncated: false,
      name: 'pdf_doc',
      tenantId: '<default>',
      variableKey: '2251799813790881',
      scopeKey: '2251799813790427',
      processInstanceKey: '2251799813790427',
      rootProcessInstanceKey: '2251799813790427',
    },
    {
      value:
        '[{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"3f34f325-143e-4e25-9be2-56195c8fd07b","contentHash":"c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974","metadata":{"contentType":"application/json","fileName":"test_json.json","expiresAt":null,"size":187,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"ff787145-b82d-4ce4-bbc4-22c44f2201b8","contentHash":"848eaa0a3565d0bc72f5d82adc229b073fa971f65a78e1010879fe5ca7cbda96","metadata":{"contentType":"application/pdf","fileName":"test_document.pdf","expiresAt":null,"size":25894,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"b6c19c47-e241-4e3a-9726-23aa7ca053ed","contentHash":"39920693ce70e3ac2dc85d8d2643afff54e153a874f18a30aa080fcabf85f572","metadata":{"contentType":"image/png","fileName":"test_image.png","expiresAt":null,"size":33906,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"3f34f325-143e-4e25-9be2-56195c8fd07b","contentHash":"c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974","metadata":{"contentType":"application/json","fileName":"test_json.json","expiresAt":null,"size":187,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"ff787145-b82d-4ce4-bbc4-22c44f2201b8","contentHash":"848eaa0a3565d0bc72f5d82adc229b073fa971f65a78e1010879fe5ca7cbda96","metadata":{"contentType":"application/pdf","fileName":"test_document.pdf","expiresAt":null,"size":25894,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"b6c19c47-e241-4e3a-9726-23aa7ca053ed","contentHash":"39920693ce70e3ac2dc85d8d2643afff54e153a874f18a30aa080fcabf85f572","metadata":{"contentType":"image/png","fileName":"test_image.png","expiresAt":null,"size":33906,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"3f34f325-143e-4e25-9be2-56195c8fd07b","contentHash":"c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974","metadata":{"contentType":"application/json","fileName":"test_json.json","expiresAt":null,"size":187,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"ff787145-b82d-4ce4-bbc4-22c44f2201b8","contentHash":"848eaa0a3565d0bc72f5d82adc229b073fa971f65a78e1010879fe5ca7cbda96","metadata":{"contentType":"application/pdf","fileName":"test_document.pdf","expiresAt":null,"size":25894,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"b6c19c47-e241-4e3a-9726-23aa7ca053ed","contentHash":"39920693ce70e3ac2dc85d8d2643afff54e153a874f18a30aa080fcabf85f572","metadata":{"contentType":"image/png","fileName":"test_image.png","expiresAt":null,"size":33906,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"3f34f325-143e-4e25-9be2-56195c8fd07b","contentHash":"c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974","metadata":{"contentType":"application/json","fileName":"test_json.json","expiresAt":null,"size":187,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"ff787145-b82d-4ce4-bbc4-22c44f2201b8","contentHash":"848eaa0a3565d0bc72f5d82adc229b073fa971f65a78e1010879fe5ca7cbda96","metadata":{"contentType":"application/pdf","fileName":"test_document.pdf","expiresAt":null,"size":25894,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"b6c19c47-e241-4e3a-9726-23aa7ca053ed","contentHash":"39920693ce70e3ac2dc85d8d2643afff54e153a874f18a30aa080fcabf85f572","metadata":{"contentType":"image/png","fileName":"test_image.png","expiresAt":null,"size":33906,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"3f34f325-143e-4e25-9be2-56195c8fd07b","contentHash":"c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974","metadata":{"contentType":"application/json","fileName":"test_json.json","expiresAt":null,"size":187,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"ff787145-b82d-4ce4-bbc4-22c44f2201b8","contentHash":"848eaa0a3565d0bc72f5d82adc229b073fa971f65a78e1010879fe5ca7cbda96","metadata":{"contentType":"application/pdf","fileName":"test_document.pdf","expiresAt":null,"size":25894,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"b6c19c47-e241-4e3a-9726-23aa7ca053ed","contentHash":"39920693ce70e3ac2dc85d8d2643afff54e153a874f18a30aa080fcabf85f572","metadata":{"contentType":"image/png","fileName":"test_image.png","expiresAt":null,"size":33906,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"3f34f325-143e-4e25-9be2-56195c8fd07b","contentHash":"c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974","metadata":{"contentType":"application/json","fileName":"test_json.json","expiresAt":null,"size":187,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"ff787145-b82d-4ce4-bbc4-22c44f2201b8","contentHash":"848eaa0a3565d0bc72f5d82adc229b073fa971f65a78e1010879fe5ca7cbda96","metadata":{"contentType":"application/pdf","fileName":"test_document.pdf","expiresAt":null,"size":25894,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"b6c19c47-e241-4e3a-9726-23aa7ca053ed","contentHash":"39920693ce70e3ac2dc85d8d2643afff54e153a874f18a30aa080fcabf85f572","metadata":{"contentType":"image/png","fileName":"test_image.png","expiresAt":null,"size":33906,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"3f34f325-143e-4e25-9be2-56195c8fd07b","contentHash":"c74786d7b2a4f02000529fe14291e9e11d7a13e4da8fe42d6f56241ca2bdf974","metadata":{"contentType":"application/json","fileName":"test_json.json","expiresAt":null,"size":187,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"ff787145-b82d-4ce4-bbc4-22c44f2201b8","contentHash":"848eaa0a3565d0bc72f5d82adc229b073fa971f65a78e1010879fe5ca7cbda96","metadata":{"contentType":"application/pdf","fileName":"test_document.pdf","expiresAt":null,"size":25894,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"b6c19c47-e241-4e3a-9726-23aa7ca053ed","contentHash":"39920693ce70e3ac2dc85d8d2643afff54e153a874f18a30aa080fcabf85f572","metadata":{"contentType":"image/png","fileName":"test_image.png","expiresAt":null,"size":33906,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"ff787145-b82d-4ce4-bbc4-22c44f2201b8","contentHash":"848eaa0a3565d0bc72f5d82adc229b073fa971f65a78e1010879fe5ca7cbda96","metadata":{"contentType":"application/pdf","fileName":"test_document.pdf","expiresAt":null,"size":25894,"processDefinitionId":null,"processInstanceKey":null,"customProperties":{}}},{"camunda.document.type":"camunda","storeId":"in-memory","documentId":"b6c19c47-e241-4e3a-',
      isTruncated: true,
      name: 'truncated_docs',
      tenantId: '<default>',
      variableKey: '2251799813790962',
      scopeKey: '2251799813790427',
      processInstanceKey: '2251799813790427',
      rootProcessInstanceKey: '2251799813790427',
    },
  ],
};

export {documentReferenceProcessInstance};
