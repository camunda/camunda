/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BatchOperationDto} from './api/sharedTypes';
import type {
  ProcessInstance,
  Variable,
  CurrentUser,
  Incident,
  ProcessDefinition,
  QueryProcessDefinitionsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {
  ProcessInstanceEntity,
  OperationEntity,
  InstanceOperationEntity,
} from 'modules/types/operate';
import type {EnhancedIncident} from './hooks/incidents';

const createRandomId = function* createRandomId(type: string) {
  let idx = 0;
  while (true) {
    yield `${type}_${idx}`;
    idx++;
  }
};

const randomIdIterator = createRandomId('id');
const randomJobIdIterator = createRandomId('jobId');
const randomFlowNodeInstanceIdIterator = createRandomId('flowNodeInstance');

function searchResult<T>(items: T[], totalItems = items.length) {
  return {items, page: {totalItems}};
}

const createIncident = (options: Partial<Incident> = {}): Incident => {
  return {
    errorMessage: 'Some Condition error has occurred',
    errorType: 'CONDITION_ERROR',
    incidentKey: randomIdIterator.next().value,
    jobKey: randomJobIdIterator.next().value,
    elementId: 'flowNodeId_alwaysFailingTask',
    elementInstanceKey: randomFlowNodeInstanceIdIterator.next().value,
    creationTime: '2019-03-01T14:26:19',
    processInstanceKey: '2251799813685294',
    processDefinitionId: 'someKey',
    processDefinitionKey: '2223894723423800',
    tenantId: '<default>',
    state: 'ACTIVE',
    ...options,
  };
};

const createEnhancedIncident = (
  options: Partial<EnhancedIncident> = {},
): EnhancedIncident => {
  const incident = createIncident(options);
  return {
    ...incident,
    processDefinitionName: 'Some Process Name',
    elementName: 'Always Failing Task',
    isSelected: false,
    ...options,
  };
};

/**
 * @returns a mocked incident Object
 * @param {*} customProps Obj with any type of custom property
 */
const createOperation = (
  options: Partial<InstanceOperationEntity> = {},
): InstanceOperationEntity => {
  return {
    id: randomIdIterator.next().value,
    errorMessage: 'string',
    state: 'SENT',
    type: 'RESOLVE_INCIDENT',
    batchOperationId: 'fe19ed17-a213-4b8d-ad10-2fb6d2bd89e5',
    completedDate: null,
    ...options,
  };
};

const createBatchOperation = (
  options: Partial<BatchOperationDto> = {},
): BatchOperationDto => {
  return {
    id: randomIdIterator.next().value,
    name: null,
    type: 'RESOLVE_INCIDENT',
    startDate: '2022-11-02T16:00:17.105+0100',
    endDate: null,
    username: 'demo',
    instancesCount: 1,
    operationsTotalCount: 1,
    operationsFinishedCount: 0,
    ...options,
  };
};

/**
 * @returns a mocked instance Object with a unique id
 * @param {*} customProps Obj with any type of custom property
 * @deprecated this function is used to create data in the format of internal API responses.
 */
const createInstance = (
  options: Partial<ProcessInstanceEntity> = {},
): ProcessInstanceEntity => {
  return {
    id: randomIdIterator.next().value,
    processId: '2',
    processName: 'someProcessName',
    processVersion: 1,
    startDate: '2018-06-21',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'someKey',
    hasActiveOperation: false,
    operations: [createOperation()],
    sortValues: [],
    parentInstanceId: null,
    rootInstanceId: null,
    callHierarchy: [],
    tenantId: '<default>',
    ...options,
  };
};

const createProcessInstance = (
  options: Partial<ProcessInstance> = {},
): ProcessInstance => {
  return {
    processInstanceKey: '2251799813685294',
    processDefinitionName: 'someProcessName',
    state: 'ACTIVE',
    processDefinitionVersion: 1,
    processDefinitionVersionTag: 'myVersionTag',
    processDefinitionId: 'someKey',
    processDefinitionKey: '2223894723423800',
    tenantId: '<default>',
    startDate: '2018-06-21',
    hasIncident: false,
    ...options,
  };
};

const createProcessDefinition = (
  options: Partial<ProcessDefinition> = {},
): ProcessDefinition => {
  return {
    name: 'Big variable process',
    processDefinitionId: 'bigVarProcess',
    processDefinitionKey: '2223894723423800',
    resourceName: 'processes/process.bpmn',
    version: 1,
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
    ...options,
  };
};

const createVariable = (options: Partial<Variable> = {}): Variable => {
  const name = options.name ?? 'testVariableName';
  return {
    variableKey: `2251799813725337-${name}`,
    name,
    value: '1',
    isTruncated: false,
    tenantId: '<default>',
    processInstanceKey: '2251799813725337',
    scopeKey: '2251799813725337',
    ...options,
  };
};

const createUser = (options: Partial<CurrentUser> = {}): CurrentUser => ({
  username: 'demo',
  displayName: 'firstname lastname',
  email: 'firstname.lastname@camunda.com',
  authorizedComponents: [],
  tenants: [],
  groups: [],
  roles: [],
  salesPlanType: null,
  c8Links: [],
  canLogout: true,
  apiUser: false,
  ...options,
});

const mockProcessDefinitions: QueryProcessDefinitionsResponseBody =
  searchResult([
    {
      name: 'New demo process',
      processDefinitionId: 'demoProcess',
      processDefinitionKey: 'demoProcess3',
      resourceName: 'processes/process.bpmn',
      version: 3,
      tenantId: '<default>',
      hasStartForm: false,
    },
    {
      name: 'Demo process',
      processDefinitionId: 'demoProcess',
      processDefinitionKey: 'demoProcess2',
      resourceName: 'processes/process.bpmn',
      version: 2,
      tenantId: '<default>',
      hasStartForm: false,
    },
    {
      name: 'Demo process',
      processDefinitionId: 'demoProcess',
      processDefinitionKey: 'demoProcess1',
      resourceName: 'processes/process.bpmn',
      version: 1,
      tenantId: '<default>',
      hasStartForm: false,
    },
    {
      name: undefined,
      processDefinitionId: 'eventBasedGatewayProcess',
      processDefinitionKey: '2251799813696866',
      resourceName: 'processes/process.bpmn',
      version: 2,
      tenantId: '<default>',
      hasStartForm: false,
    },
    {
      name: 'Event based gateway with message start',
      processDefinitionId: 'eventBasedGatewayProcess',
      processDefinitionKey: '2251799813685911',
      resourceName: 'processes/process.bpmn',
      version: 1,
      tenantId: '<default>',
      hasStartForm: false,
    },
    {
      name: 'Big variable process',
      processDefinitionId: 'bigVarProcess',
      processDefinitionKey: '2251799813685892',
      resourceName: 'processes/process.bpmn',
      version: 1,
      versionTag: 'MyVersionTag',
      tenantId: '<default>',
      hasStartForm: false,
    },
    {
      name: 'Big variable process',
      processDefinitionId: 'bigVarProcess',
      processDefinitionKey: '2251799813685893',
      resourceName: 'processes/process.bpmn',
      version: 2,
      tenantId: '<tenant-A>',
      hasStartForm: false,
    },
    {
      name: 'Big variable process',
      processDefinitionId: 'bigVarProcess',
      processDefinitionKey: '2251799813685894',
      resourceName: 'processes/process.bpmn',
      version: 1,
      tenantId: '<tenant-A>',
      hasStartForm: false,
    },
    {
      name: 'Order',
      processDefinitionId: 'orderProcess',
      processDefinitionKey: 'orderProcess1',
      resourceName: 'processes/process.bpmn',
      version: 1,
      tenantId: '<default>',
      hasStartForm: false,
    },
  ]);

/**
 * @returns a mocked diagramNode Object with a unique id
 * @param {*} customProps Obj with any type of custom property
 */
const createDiagramNode = (options = {}) => {
  return {
    id: 'StartEvent_1',
    name: 'Start Event',
    $type: 'bpmn:StartEvent',

    $instanceOf: (type: string) => type === 'bpmn:StartEvent',
    ...options,
  };
};

const mockProcessStatistics = {
  items: [
    {
      elementId: 'ServiceTask_0kt6c5i',
      active: 1,
      canceled: 0,
      incidents: 0,
      completed: 0,
    },
  ],
};

const mockMultipleStatesStatistics = {
  items: [
    {
      elementId: 'EndEvent_042s0oc',
      active: 1,
      canceled: 2,
      incidents: 3,
      completed: 4,
    },
  ],
};

const mockProcessXML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_1771k9d" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Zeebe Modeler" exporterVersion="0.5.0">
  <bpmn:process id="bigVarProcess" isExecutable="true" name="Big variable process">
    <bpmn:startEvent id="StartEvent_1" name="Start Event 1">
      <bpmn:outgoing>SequenceFlow_04ev4jl</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="ServiceTask_0kt6c5i" name="Service Task 1">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="task" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_04ev4jl</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_1njhlr0</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:sequenceFlow id="SequenceFlow_04ev4jl" sourceRef="StartEvent_1" targetRef="ServiceTask_0kt6c5i" />
    <bpmn:endEvent id="EndEvent_0crvjrk" name="End Event">
      <bpmn:incoming>SequenceFlow_1njhlr0</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="SequenceFlow_1njhlr0" sourceRef="ServiceTask_0kt6c5i" targetRef="EndEvent_0crvjrk" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="process">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="173" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_0kt6c5i_di" bpmnElement="ServiceTask_0kt6c5i">
        <dc:Bounds x="259" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_04ev4jl_di" bpmnElement="SequenceFlow_04ev4jl">
        <di:waypoint x="209" y="120" />
        <di:waypoint x="259" y="120" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="EndEvent_0crvjrk_di" bpmnElement="EndEvent_0crvjrk">
        <dc:Bounds x="409" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_1njhlr0_di" bpmnElement="SequenceFlow_1njhlr0">
        <di:waypoint x="359" y="120" />
        <di:waypoint x="409" y="120" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

const mockProcessWithInputOutputMappingsXML = `<?xml version="1.0" encoding="UTF-8"?><bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:modeler="http://camunda.org/schema/modeler/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Web Modeler" exporterVersion="eb9fa7e" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.0.0" camunda:diagramRelationId="9ee67cec-c2eb-4b0d-968b-f7a9ae3d6d3d">
<bpmn:process id="Process_b1711b2e-ec8e-4dad-908c-8c12e028f32f" name="Input Output Mapping Test" isExecutable="true">
  <bpmn:startEvent id="StartEvent_1">
    <bpmn:outgoing>Flow_17h9txj</bpmn:outgoing>
  </bpmn:startEvent>
  <bpmn:sequenceFlow id="Flow_17h9txj" sourceRef="StartEvent_1" targetRef="Activity_0qtp1k6"/>
  <bpmn:callActivity id="Activity_0qtp1k6">
    <bpmn:extensionElements>
      <zeebe:calledElement processId="called-element-test" propagateAllChildVariables="false"/>
      <zeebe:ioMapping>
        <zeebe:input source="= &quot;test1&quot;" target="localVariable1"/>
        <zeebe:input source="= &quot;test2&quot;" target="localVariable2"/>
        <zeebe:output source="= 2" target="outputTest"/>
      </zeebe:ioMapping>
    </bpmn:extensionElements>
    <bpmn:incoming>Flow_17h9txj</bpmn:incoming>
    <bpmn:outgoing>Flow_02c6e87</bpmn:outgoing>
  </bpmn:callActivity>
  <bpmn:endEvent id="Event_0bonl61">
    <bpmn:incoming>Flow_02c6e87</bpmn:incoming>
  </bpmn:endEvent>
  <bpmn:sequenceFlow id="Flow_02c6e87" sourceRef="Activity_0qtp1k6" targetRef="Event_0bonl61"/>
</bpmn:process>
<bpmndi:BPMNDiagram id="BPMNDiagram_1">
  <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_b1711b2e-ec8e-4dad-908c-8c12e028f32f">
    <bpmndi:BPMNEdge id="Flow_02c6e87_di" bpmnElement="Flow_02c6e87">
      <di:waypoint x="340" y="118"/>
      <di:waypoint x="402" y="118"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNEdge id="Flow_17h9txj_di" bpmnElement="Flow_17h9txj">
      <di:waypoint x="186" y="118"/>
      <di:waypoint x="240" y="118"/>
    </bpmndi:BPMNEdge>
    <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
      <dc:Bounds x="150" y="100" width="36" height="36"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Activity_1spoc9v_di" bpmnElement="Activity_0qtp1k6">
      <dc:Bounds x="240" y="78" width="100" height="80"/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNShape id="Event_0bonl61_di" bpmnElement="Event_0bonl61">
      <dc:Bounds x="402" y="100" width="36" height="36"/>
    </bpmndi:BPMNShape>
  </bpmndi:BPMNPlane>
</bpmndi:BPMNDiagram>
</bpmn:definitions>`;

const mockCallActivityProcessXML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_1e4hrq2" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="4.8.1" modeler:executionPlatform="Camunda Platform" modeler:executionPlatformVersion="7.15.0">
  <bpmn:process id="Process_0r3smqt" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>Flow_1mxj2rr</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:endEvent id="Event_1db567d">
      <bpmn:incoming>Flow_0p62350</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_0p62350" sourceRef="Activity_0zqism7" targetRef="Event_1db567d" />
    <bpmn:sequenceFlow id="Flow_1mxj2rr" sourceRef="StartEvent_1" targetRef="Activity_0zqism7" />
    <bpmn:callActivity id="Activity_0zqism7">
      <bpmn:incoming>Flow_1mxj2rr</bpmn:incoming>
      <bpmn:outgoing>Flow_0p62350</bpmn:outgoing>
    </bpmn:callActivity>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_0r3smqt">
      <bpmndi:BPMNEdge id="Flow_0p62350_di" bpmnElement="Flow_0p62350">
        <di:waypoint x="360" y="117" />
        <di:waypoint x="412" y="117" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1mxj2rr_di" bpmnElement="Flow_1mxj2rr">
        <di:waypoint x="215" y="117" />
        <di:waypoint x="260" y="117" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="179" y="99" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Event_1db567d_di" bpmnElement="Event_1db567d">
        <dc:Bounds x="412" y="99" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_11fxkyy_di" bpmnElement="Activity_0zqism7">
        <dc:Bounds x="260" y="77" width="100" height="80" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

const operations: OperationEntity[] = [
  {
    id: '921455fd-849a-49c5-be17-c92eb6d9e946',
    name: null,
    type: 'CANCEL_PROCESS_INSTANCE',
    startDate: '2020-09-30T06:02:32.748+0000',
    endDate: '2020-09-29T15:38:34.372+0000',
    instancesCount: 1,
    operationsTotalCount: 1,
    operationsFinishedCount: 0,
    sortValues: ['9223372036854775807', '1601445752748'],
  },
  {
    id: 'd116b2a3-eb19-47f1-85c0-60b3e1814aa2',
    name: null,
    type: 'CANCEL_PROCESS_INSTANCE',
    startDate: '2020-09-29T15:37:20.187+0000',
    endDate: '2020-09-29T15:38:34.372+0000',
    instancesCount: 1,
    operationsTotalCount: 1,
    operationsFinishedCount: 1,
    sortValues: ['1601393914372', '1601393840187'],
  },
  {
    id: '68d41595-bbee-49d0-84c8-8713dc8584d5',
    name: null,
    type: 'CANCEL_PROCESS_INSTANCE',
    startDate: '2020-09-29T15:37:16.052+0000',
    endDate: '2020-09-29T15:38:22.227+0000',
    instancesCount: 1,
    operationsTotalCount: 1,
    operationsFinishedCount: 1,
    sortValues: ['1601393902227', '1601393836052'],
  },
];

const multiInstanceProcess = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_1kgscet" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="1.16.0">
  <bpmn:process id="multiInstanceProcess" name="Multi-Instance Process" isExecutable="true">
    <bpmn:startEvent id="start" name="Start">
      <bpmn:outgoing>SequenceFlow_0ywev43</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="SequenceFlow_0ywev43" sourceRef="start" targetRef="peterFork" />
    <bpmn:parallelGateway id="peterFork" name="Peter Fork">
      <bpmn:incoming>SequenceFlow_0ywev43</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_0rzup48</bpmn:outgoing>
      <bpmn:outgoing>SequenceFlow_05xgu65</bpmn:outgoing>
      <bpmn:outgoing>SequenceFlow_09ulah7</bpmn:outgoing>
    </bpmn:parallelGateway>
    <bpmn:exclusiveGateway id="peterJoin" name="Peter Join">
      <bpmn:incoming>SequenceFlow_0rzup48</bpmn:incoming>
      <bpmn:incoming>SequenceFlow_05xgu65</bpmn:incoming>
      <bpmn:incoming>SequenceFlow_09ulah7</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_09pqj2f</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:sequenceFlow id="SequenceFlow_0rzup48" sourceRef="peterFork" targetRef="peterJoin" />
    <bpmn:serviceTask id="reduceTask" name="Reduce">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="reduce" retries="1" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_0lfp9em</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_0pynv0i</bpmn:outgoing>
      <bpmn:multiInstanceLoopCharacteristics isSequential="true">
        <bpmn:extensionElements>
          <zeebe:loopCharacteristics inputCollection="=items" inputElement="item" />
        </bpmn:extensionElements>
      </bpmn:multiInstanceLoopCharacteristics>
    </bpmn:serviceTask>
    <bpmn:subProcess id="filterMapSubProcess" name="Filter-Map Sub Process">
      <bpmn:incoming>SequenceFlow_09pqj2f</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_0lfp9em</bpmn:outgoing>
      <bpmn:multiInstanceLoopCharacteristics>
        <bpmn:extensionElements>
          <zeebe:loopCharacteristics inputCollection="=items" inputElement="item" />
        </bpmn:extensionElements>
      </bpmn:multiInstanceLoopCharacteristics>
      <bpmn:startEvent id="startFilterMap" name="Start&#10;Filter-Map">
        <bpmn:outgoing>SequenceFlow_1denv3y</bpmn:outgoing>
      </bpmn:startEvent>
      <bpmn:serviceTask id="filterTask" name="Filter">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="filter" retries="1" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_1denv3y</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_1vxqfdy</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:sequenceFlow id="SequenceFlow_1denv3y" sourceRef="startFilterMap" targetRef="filterTask" />
      <bpmn:serviceTask id="mapTask" name="Map">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="map" retries="1" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_1vxqfdy</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_106qs66</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:sequenceFlow id="SequenceFlow_1vxqfdy" sourceRef="filterTask" targetRef="mapTask" />
      <bpmn:endEvent id="endFilterMap" name="End&#10;FilterMap&#10;">
        <bpmn:incoming>SequenceFlow_106qs66</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:sequenceFlow id="SequenceFlow_106qs66" sourceRef="mapTask" targetRef="endFilterMap" />
    </bpmn:subProcess>
    <bpmn:endEvent id="end" name="End">
      <bpmn:incoming>SequenceFlow_0pynv0i</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="SequenceFlow_05xgu65" sourceRef="peterFork" targetRef="peterJoin" />
    <bpmn:sequenceFlow id="SequenceFlow_09ulah7" sourceRef="peterFork" targetRef="peterJoin" />
    <bpmn:sequenceFlow id="SequenceFlow_0lfp9em" sourceRef="filterMapSubProcess" targetRef="reduceTask" />
    <bpmn:sequenceFlow id="SequenceFlow_09pqj2f" sourceRef="peterJoin" targetRef="filterMapSubProcess" />
    <bpmn:sequenceFlow id="SequenceFlow_0pynv0i" sourceRef="reduceTask" targetRef="end" />
    <bpmn:textAnnotation id="TextAnnotation_077lfkg">
      <bpmn:text>Fork to simulate peter case</bpmn:text>
    </bpmn:textAnnotation>
    <bpmn:association id="Association_1bsuyxn" sourceRef="peterFork" targetRef="TextAnnotation_077lfkg" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="multiInstanceProcess">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="start">
        <dc:Bounds x="179" y="159" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="186" y="202" width="24" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_0ywev43_di" bpmnElement="SequenceFlow_0ywev43">
        <di:waypoint x="215" y="177" />
        <di:waypoint x="263" y="177" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="ParallelGateway_195w4tj_di" bpmnElement="peterFork">
        <dc:Bounds x="263" y="152" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="230" y="206" width="53" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ExclusiveGateway_00qjrsu_di" bpmnElement="peterJoin" isMarkerVisible="true">
        <dc:Bounds x="380" y="152" width="50" height="50" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="343" y="207" width="50" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_0rzup48_di" bpmnElement="SequenceFlow_0rzup48">
        <di:waypoint x="313" y="177" />
        <di:waypoint x="380" y="177" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="ServiceTask_19tlu7r_di" bpmnElement="reduceTask">
        <dc:Bounds x="979" y="137" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="SubProcess_0wql3ps_di" bpmnElement="filterMapSubProcess" isExpanded="true">
        <dc:Bounds x="508" y="77" width="399" height="200" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1kwl5m7_di" bpmnElement="end">
        <dc:Bounds x="1139" y="159" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="1147" y="202" width="20" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="StartEvent_11uxmcd_di" bpmnElement="startFilterMap">
        <dc:Bounds x="529" y="155" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="525" y="198" width="50" height="27" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_0bw8npn_di" bpmnElement="filterTask">
        <dc:Bounds x="601" y="133" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_1denv3y_di" bpmnElement="SequenceFlow_1denv3y">
        <di:waypoint x="565" y="173" />
        <di:waypoint x="601" y="173" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="ServiceTask_1c90zv4_di" bpmnElement="mapTask">
        <dc:Bounds x="735" y="133" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_1vxqfdy_di" bpmnElement="SequenceFlow_1vxqfdy">
        <di:waypoint x="701" y="173" />
        <di:waypoint x="735" y="173" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="EndEvent_1r5xmm2_di" bpmnElement="endFilterMap">
        <dc:Bounds x="856" y="155" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="852" y="198" width="47" height="40" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_106qs66_di" bpmnElement="SequenceFlow_106qs66">
        <di:waypoint x="835" y="173" />
        <di:waypoint x="856" y="173" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_05xgu65_di" bpmnElement="SequenceFlow_05xgu65">
        <di:waypoint x="288" y="177" />
        <di:waypoint x="288" y="268" />
        <di:waypoint x="405" y="268" />
        <di:waypoint x="405" y="202" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_09ulah7_di" bpmnElement="SequenceFlow_09ulah7">
        <di:waypoint x="288" y="152" />
        <di:waypoint x="288" y="86" />
        <di:waypoint x="405" y="86" />
        <di:waypoint x="405" y="152" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="TextAnnotation_077lfkg_di" bpmnElement="TextAnnotation_077lfkg">
        <dc:Bounds x="176" y="12" width="100" height="40" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Association_1bsuyxn_di" bpmnElement="Association_1bsuyxn">
        <di:waypoint x="281" y="159" />
        <di:waypoint x="235" y="52" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_0lfp9em_di" bpmnElement="SequenceFlow_0lfp9em">
        <di:waypoint x="907" y="177" />
        <di:waypoint x="979" y="177" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_09pqj2f_di" bpmnElement="SequenceFlow_09pqj2f">
        <di:waypoint x="430" y="177" />
        <di:waypoint x="508" y="177" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_0pynv0i_di" bpmnElement="SequenceFlow_0pynv0i">
        <di:waypoint x="1079" y="177" />
        <di:waypoint x="1139" y="177" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

const eventSubProcess = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" id="Definitions_0uef7zo" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Zeebe Modeler" exporterVersion="0.8.0">
  <bpmn:process id="eventSubprocessProcess" name="Event Subprocess Process" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1vnazga">
      <bpmn:outgoing>SequenceFlow_0b1strv</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:endEvent id="EndEvent_03acvim">
      <bpmn:incoming>SequenceFlow_0ogmd2w</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="SequenceFlow_0b1strv" sourceRef="StartEvent_1vnazga" targetRef="ServiceTask_1daop2o" />
    <bpmn:subProcess id="SubProcess_1ip6c6s" name="Event Subprocess" triggeredByEvent="true">
      <bpmn:endEvent id="EndEvent_1uddjvh">
        <bpmn:incoming>SequenceFlow_10d38p0</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:serviceTask id="ServiceTask_0h8cwwl" name="Event Subprocess task">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="eventSupbprocessTask" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_0xk369x</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_10d38p0</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:sequenceFlow id="SequenceFlow_10d38p0" sourceRef="ServiceTask_0h8cwwl" targetRef="EndEvent_1uddjvh" />
      <bpmn:sequenceFlow id="SequenceFlow_0xk369x" sourceRef="StartEvent_1u9mwoj" targetRef="ServiceTask_0h8cwwl" />
      <bpmn:startEvent id="StartEvent_1u9mwoj" name="Interrupting timer">
        <bpmn:outgoing>SequenceFlow_0xk369x</bpmn:outgoing>
        <bpmn:timerEventDefinition>
          <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">PT3M</bpmn:timeDuration>
        </bpmn:timerEventDefinition>
      </bpmn:startEvent>
    </bpmn:subProcess>
    <bpmn:serviceTask id="ServiceTask_1daop2o" name="Parent process task">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="parentProcessTask" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_0b1strv</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_1aytoqp</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:sequenceFlow id="SequenceFlow_1aytoqp" sourceRef="ServiceTask_1daop2o" targetRef="ServiceTask_0ruokei" />
    <bpmn:subProcess id="ServiceTask_0ruokei">
      <bpmn:incoming>SequenceFlow_1aytoqp</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_0ogmd2w</bpmn:outgoing>
      <bpmn:startEvent id="StartEvent_1dgs6mf">
        <bpmn:outgoing>SequenceFlow_03jyud1</bpmn:outgoing>
      </bpmn:startEvent>
      <bpmn:serviceTask id="ServiceTask_0wfdfpx" name="Subprocess task">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="subprocessTask" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_03jyud1</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_1ey1yvq</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:sequenceFlow id="SequenceFlow_03jyud1" sourceRef="StartEvent_1dgs6mf" targetRef="ServiceTask_0wfdfpx" />
      <bpmn:endEvent id="EndEvent_171a64z">
        <bpmn:incoming>SequenceFlow_1ey1yvq</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:sequenceFlow id="SequenceFlow_1ey1yvq" sourceRef="ServiceTask_0wfdfpx" targetRef="EndEvent_171a64z" />
      <bpmn:subProcess id="SubProcess_006dg16" name="Event Subprocess inside Subprocess" triggeredByEvent="true">
        <bpmn:endEvent id="EndEvent_0dq3i8l">
          <bpmn:incoming>SequenceFlow_0vkqogh</bpmn:incoming>
        </bpmn:endEvent>
        <bpmn:serviceTask id="ServiceTask_0cj9pdg" name="Task in sub-subprocess">
          <bpmn:extensionElements>
            <zeebe:taskDefinition type="subSubprocessTask" />
          </bpmn:extensionElements>
          <bpmn:incoming>SequenceFlow_1c82aad</bpmn:incoming>
          <bpmn:outgoing>SequenceFlow_0vkqogh</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:sequenceFlow id="SequenceFlow_1c82aad" sourceRef="StartEvent_0kpitfv" targetRef="ServiceTask_0cj9pdg" />
        <bpmn:sequenceFlow id="SequenceFlow_0vkqogh" sourceRef="ServiceTask_0cj9pdg" targetRef="EndEvent_0dq3i8l" />
        <bpmn:startEvent id="StartEvent_0kpitfv" name="Timer in sub-subprocess" isInterrupting="false">
          <bpmn:outgoing>SequenceFlow_1c82aad</bpmn:outgoing>
          <bpmn:timerEventDefinition>
            <bpmn:timeCycle xsi:type="bpmn:tFormalExpression">R2/PT5S</bpmn:timeCycle>
          </bpmn:timerEventDefinition>
        </bpmn:startEvent>
      </bpmn:subProcess>
    </bpmn:subProcess>
    <bpmn:sequenceFlow id="SequenceFlow_0ogmd2w" sourceRef="ServiceTask_0ruokei" targetRef="EndEvent_03acvim" />
  </bpmn:process>
  <bpmn:message id="Message_03ggk3d" name="interruptProcess">
    <bpmn:extensionElements>
      <zeebe:subscription correlationKey="=clientId" />
    </bpmn:extensionElements>
  </bpmn:message>
  <bpmn:message id="Message_1nvz8ri" name="continueProcess">
    <bpmn:extensionElements>
      <zeebe:subscription correlationKey="=clientId" />
    </bpmn:extensionElements>
  </bpmn:message>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="eventSubprocessProcess">
      <bpmndi:BPMNShape id="StartEvent_1vnazga_di" bpmnElement="StartEvent_1vnazga">
        <dc:Bounds x="212" y="252" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_03acvim_di" bpmnElement="EndEvent_03acvim">
        <dc:Bounds x="1202" y="242" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_0b1strv_di" bpmnElement="SequenceFlow_0b1strv">
        <di:waypoint x="248" y="270" />
        <di:waypoint x="344" y="270" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="SubProcess_1u7mexg_di" bpmnElement="SubProcess_1ip6c6s" isExpanded="true">
        <dc:Bounds x="200" y="500" width="388" height="180" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1uddjvh_di" bpmnElement="EndEvent_1uddjvh">
        <dc:Bounds x="512" y="582" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_0h8cwwl_di" bpmnElement="ServiceTask_0h8cwwl">
        <dc:Bounds x="350" y="560" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_10d38p0_di" bpmnElement="SequenceFlow_10d38p0">
        <di:waypoint x="450" y="600" />
        <di:waypoint x="512" y="600" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_0xk369x_di" bpmnElement="SequenceFlow_0xk369x">
        <di:waypoint x="288" y="600" />
        <di:waypoint x="350" y="600" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="ServiceTask_1daop2o_di" bpmnElement="ServiceTask_1daop2o">
        <dc:Bounds x="344" y="230" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_1aytoqp_di" bpmnElement="SequenceFlow_1aytoqp">
        <di:waypoint x="444" y="270" />
        <di:waypoint x="530" y="270" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="SubProcess_1aoke6f_di" bpmnElement="ServiceTask_0ruokei" isExpanded="true">
        <dc:Bounds x="530" y="85" width="590" height="370" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="StartEvent_1dgs6mf_di" bpmnElement="StartEvent_1dgs6mf">
        <dc:Bounds x="660.3333333333333" y="167" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_0ogmd2w_di" bpmnElement="SequenceFlow_0ogmd2w">
        <di:waypoint x="1120" y="260" />
        <di:waypoint x="1202" y="260" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="ServiceTask_0wfdfpx_di" bpmnElement="ServiceTask_0wfdfpx">
        <dc:Bounds x="740" y="145" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_03jyud1_di" bpmnElement="SequenceFlow_03jyud1">
        <di:waypoint x="696" y="185" />
        <di:waypoint x="740" y="185" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="EndEvent_171a64z_di" bpmnElement="EndEvent_171a64z">
        <dc:Bounds x="882" y="167" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_1ey1yvq_di" bpmnElement="SequenceFlow_1ey1yvq">
        <di:waypoint x="840" y="185" />
        <di:waypoint x="882" y="185" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="SubProcess_006dg16_di" bpmnElement="SubProcess_006dg16" isExpanded="true">
        <dc:Bounds x="630" y="270" width="388" height="145" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_0dq3i8l_di" bpmnElement="EndEvent_0dq3i8l">
        <dc:Bounds x="942" y="317" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_0cj9pdg_di" bpmnElement="ServiceTask_0cj9pdg">
        <dc:Bounds x="780" y="295" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_1c82aad_di" bpmnElement="SequenceFlow_1c82aad">
        <di:waypoint x="718" y="335" />
        <di:waypoint x="780" y="335" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_0vkqogh_di" bpmnElement="SequenceFlow_0vkqogh">
        <di:waypoint x="880" y="335" />
        <di:waypoint x="942" y="335" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="StartEvent_08k6psq_di" bpmnElement="StartEvent_0kpitfv">
        <dc:Bounds x="682" y="317" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="669" y="360" width="65" height="27" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="StartEvent_0d2wour_di" bpmnElement="StartEvent_1u9mwoj">
        <dc:Bounds x="252" y="582" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="228" y="625" width="85" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

const adHocSubProcessInnerInstance = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_1fekqd5" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.41.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.8.0">
  <bpmn:process id="ad_hoc_inner_subprocess_test" isExecutable="true">
    <bpmn:startEvent id="start_event">
      <bpmn:outgoing>Flow_1mi8489</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_1mi8489" sourceRef="start_event" targetRef="ad_hoc_subprocess" />
    <bpmn:endEvent id="end_event">
      <bpmn:incoming>Flow_0em90ai</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_0em90ai" sourceRef="ad_hoc_subprocess" targetRef="end_event" />
    <bpmn:adHocSubProcess id="ad_hoc_subprocess" zeebe:modelerTemplate="io.camunda.connectors.agenticai.aiagent.jobworker.v1" zeebe:modelerTemplateVersion="5" zeebe:modelerTemplateIcon="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHZpZXdCb3g9IjAgMCAzMiAzMiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPGNpcmNsZSBjeD0iMTYiIGN5PSIxNiIgcj0iMTYiIGZpbGw9IiNBNTZFRkYiLz4KPG1hc2sgaWQ9InBhdGgtMi1vdXRzaWRlLTFfMTg1XzYiIG1hc2tVbml0cz0idXNlclNwYWNlT25Vc2UiIHg9IjQiIHk9IjQiIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgZmlsbD0iYmxhY2siPgo8cmVjdCBmaWxsPSJ3aGl0ZSIgeD0iNCIgeT0iNCIgd2lkdGg9IjI0IiBoZWlnaHQ9IjI0Ii8+CjxwYXRoIGZpbGwtcnVsZT0iZXZlbm9kZCIgY2xpcC1ydWxlPSJldmVub2RkIiBkPSJNMjAuMDEwNSAxMi4wOTg3QzE4LjQ5IDEwLjU4OTQgMTcuMTU5NCA4LjEwODE0IDE2LjE3OTkgNi4wMTEwM0MxNi4xNTIgNi4wMDQ1MSAxNi4xMTc2IDYgMTYuMDc5NCA2QzE2LjA0MTEgNiAxNi4wMDY2IDYuMDA0NTEgMTUuOTc4OCA2LjAxMTA0QzE0Ljk5OTQgOC4xMDgxNCAxMy42Njk3IDEwLjU4ODkgMTIuMTQ4MSAxMi4wOTgxQzEwLjYyNjkgMTMuNjA3MSA4LjEyNTY4IDE0LjkyNjQgNi4wMTE1NyAxNS44OTgxQzYuMDA0NzQgMTUuOTI2MSA2IDE1Ljk2MTEgNiAxNkM2IDE2LjAzODcgNi4wMDQ2OCAxNi4wNzM2IDYuMDExNDQgMTYuMTAxNEM4LjEyNTE5IDE3LjA3MjkgMTAuNjI2MiAxOC4zOTE5IDEyLjE0NzcgMTkuOTAxNkMxMy42Njk3IDIxLjQxMDcgMTQuOTk5NiAyMy44OTIgMTUuOTc5MSAyNS45ODlDMTYuMDA2OCAyNS45OTU2IDE2LjA0MTEgMjYgMTYuMDc5MyAyNkMxNi4xMTc1IDI2IDE2LjE1MTkgMjUuOTk1NCAxNi4xNzk2IDI1Ljk4OUMxNy4xNTkxIDIzLjg5MiAxOC40ODg4IDIxLjQxMSAyMC4wMDk5IDE5LjkwMjFNMjAuMDA5OSAxOS45MDIxQzIxLjUyNTMgMTguMzk4NyAyMy45NDY1IDE3LjA2NjkgMjUuOTkxNSAxNi4wODI0QzI1Ljk5NjUgMTYuMDU5MyAyNiAxNi4wMzEgMjYgMTUuOTk5N0MyNiAxNS45Njg0IDI1Ljk5NjUgMTUuOTQwMyAyNS45OTE1IDE1LjkxNzFDMjMuOTQ3NCAxNC45MzI3IDIxLjUyNTkgMTMuNjAxIDIwLjAxMDUgMTIuMDk4NyIvPgo8L21hc2s+CjxwYXRoIGZpbGwtcnVsZT0iZXZlbm9kZCIgY2xpcC1ydWxlPSJldmVub2RkIiBkPSJNMjAuMDEwNSAxMi4wOTg3QzE4LjQ5IDEwLjU4OTQgMTcuMTU5NCA4LjEwODE0IDE2LjE3OTkgNi4wMTEwM0MxNi4xNTIgNi4wMDQ1MSAxNi4xMTc2IDYgMTYuMDc5NCA2QzE2LjA0MTEgNiAxNi4wMDY2IDYuMDA0NTEgMTUuOTc4OCA2LjAxMTA0QzE0Ljk5OTQgOC4xMDgxNCAxMy42Njk3IDEwLjU4ODkgMTIuMTQ4MSAxMi4wOTgxQzEwLjYyNjkgMTMuNjA3MSA4LjEyNTY4IDE0LjkyNjQgNi4wMTE1NyAxNS44OTgxQzYuMDA0NzQgMTUuOTI2MSA2IDE1Ljk2MTEgNiAxNkM2IDE2LjAzODcgNi4wMDQ2OCAxNi4wNzM2IDYuMDExNDQgMTYuMTAxNEM4LjEyNTE5IDE3LjA3MjkgMTAuNjI2MiAxOC4zOTE5IDEyLjE0NzcgMTkuOTAxNkMxMy42Njk3IDIxLjQxMDcgMTQuOTk5NiAyMy44OTIgMTUuOTc5MSAyNS45ODlDMTYuMDA2OCAyNS45OTU2IDE2LjA0MTEgMjYgMTYuMDc5MyAyNkMxNi4xMTc1IDI2IDE2LjE1MTkgMjUuOTk1NCAxNi4xNzk2IDI1Ljk4OUMxNy4xNTkxIDIzLjg5MiAxOC40ODg4IDIxLjQxMSAyMC4wMDk5IDE5LjkwMjFNMjAuMDA5OSAxOS45MDIxQzIxLjUyNTMgMTguMzk4NyAyMy45NDY1IDE3LjA2NjkgMjUuOTkxNSAxNi4wODI0QzI1Ljk5NjUgMTYuMDU5MyAyNiAxNi4wMzEgMjYgMTUuOTk5N0MyNiAxNS45Njg0IDI1Ljk5NjUgMTUuOTQwMyAyNS45OTE1IDE1LjkxNzFDMjMuOTQ3NCAxNC45MzI3IDIxLjUyNTkgMTMuNjAxIDIwLjAxMDUgMTIuMDk4NyIgZmlsbD0id2hpdGUiLz4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0yMC4wMTA1IDEyLjA5ODdDMTguNDkgMTAuNTg5NCAxNy4xNTk0IDguMTA4MTQgMTYuMTc5OSA2LjAxMTAzQzE2LjE1MiA2LjAwNDUxIDE2LjExNzYgNiAxNi4wNzk0IDZDMTYuMDQxMSA2IDE2LjAwNjYgNi4wMDQ1MSAxNS45Nzg4IDYuMDExMDRDMTQuOTk5NCA4LjEwODE0IDEzLjY2OTcgMTAuNTg4OSAxMi4xNDgxIDEyLjA5ODFDMTAuNjI2OSAxMy42MDcxIDguMTI1NjggMTQuOTI2NCA2LjAxMTU3IDE1Ljg5ODFDNi4wMDQ3NCAxNS45MjYxIDYgMTUuOTYxMSA2IDE2QzYgMTYuMDM4NyA2LjAwNDY4IDE2LjA3MzYgNi4wMTE0NCAxNi4xMDE0QzguMTI1MTkgMTcuMDcyOSAxMC42MjYyIDE4LjM5MTkgMTIuMTQ3NyAxOS45MDE2QzEzLjY2OTcgMjEuNDEwNyAxNC45OTk2IDIzLjg5MiAxNS45NzkxIDI1Ljk4OUMxNi4wMDY4IDI1Ljk5NTYgMTYuMDQxMSAyNiAxNi4wNzkzIDI2QzE2LjExNzUgMjYgMTYuMTUxOSAyNS45OTU0IDE2LjE3OTYgMjUuOTg5QzE3LjE1OTEgMjMuODkyIDE4LjQ4ODggMjEuNDExIDIwLjAwOTkgMTkuOTAyMU0yMC4wMDk5IDE5LjkwMjFDMjEuNTI1MyAxOC4zOTg3IDIzLjk0NjUgMTcuMDY2OSAyNS45OTE1IDE2LjA4MjRDMjUuOTk2NSAxNi4wNTkzIDI2IDE2LjAzMSAyNiAxNS45OTk3QzI2IDE1Ljk2ODQgMjUuOTk2NSAxNS45NDAzIDI1Ljk5MTUgMTUuOTE3MUMyMy45NDc0IDE0LjkzMjcgMjEuNTI1OSAxMy42MDEgMjAuMDEwNSAxMi4wOTg3IiBzdHJva2U9IiM0OTFEOEIiIHN0cm9rZS13aWR0aD0iNCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCIgbWFzaz0idXJsKCNwYXRoLTItb3V0c2lkZS0xXzE4NV82KSIvPgo8L3N2Zz4K">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="io.camunda.agenticai:aiagent-job-worker:1" retries="3" />
        <zeebe:ioMapping>
          <zeebe:input source="anthropic" target="provider.type" />
          <zeebe:input source="xxxx" target="provider.anthropic.authentication.apiKey" />
          <zeebe:input source="claude-3-5-sonnet-20240620" target="provider.anthropic.model.model" />
          <zeebe:input source="=&#34;You are **TaskAgent**, a helpful, generic chat agent that can handle a wide variety of customer requests using your own domain knowledge **and** any tools explicitly provided to you at runtime.&#10;&#10;If tools are provided, you should prefer them instead of guessing an answer. You can call the same tool multiple times by providing different input values. Don&#39;t guess any tools which were not explicitly configured. If no tool matches the request, try to generate an answer. If you&#39;re not able to find a good answer, return with a message stating why you&#39;re not able to.&#10;&#10;Wrap minimal, inspectable reasoning in *exactly* this XML template:&#10;&#10;&#60;thinking&#62;&#10;&#60;context&#62;…briefly state the customer’s need and current state…&#60;/context&#62;&#10;&#60;reflection&#62;…list candidate tools, justify which you will call next and why…&#60;/reflection&#62;&#10;&#60;/thinking&#62;&#10;&#10;Reveal **no** additional private reasoning outside these tags.&#34;" target="data.systemPrompt.prompt" />
          <zeebe:input source="=foo" target="data.userPrompt.prompt" />
          <zeebe:input target="agentContext" />
          <zeebe:input source="in-process" target="data.memory.storage.type" />
          <zeebe:input source="=20" target="data.memory.contextWindowSize" />
          <zeebe:input source="=10" target="data.limits.maxModelCalls" />
          <zeebe:input source="WAIT_FOR_TOOL_CALL_RESULTS" target="data.events.behavior" />
          <zeebe:input source="text" target="data.response.format.type" />
          <zeebe:input source="=false" target="data.response.format.parseJson" />
          <zeebe:input source="=false" target="data.response.includeAssistantMessage" />
          <zeebe:input source="=false" target="data.response.includeAgentContext" />
          <zeebe:output source="=agent" target="agent" />
        </zeebe:ioMapping>
        <zeebe:taskHeaders>
          <zeebe:header key="elementTemplateVersion" value="5" />
          <zeebe:header key="elementTemplateId" value="io.camunda.connectors.agenticai.aiagent.jobworker.v1" />
          <zeebe:header key="retryBackoff" value="PT0S" />
        </zeebe:taskHeaders>
        <zeebe:adHoc outputCollection="toolCallResults" outputElement="={&#10;  id: toolCall._meta.id,&#10;  name: toolCall._meta.name,&#10;  content: toolCallResult&#10;}" />
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1mi8489</bpmn:incoming>
      <bpmn:outgoing>Flow_0em90ai</bpmn:outgoing>
      <bpmn:userTask id="user_task_in_ad_hoc_subprocess">
        <bpmn:extensionElements>
          <zeebe:userTask />
        </bpmn:extensionElements>
      </bpmn:userTask>
    </bpmn:adHocSubProcess>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="ad_hoc_inner_subprocess_test">
      <bpmndi:BPMNShape id="Event_18tmc7l_di" bpmnElement="end_event">
        <dc:Bounds x="792" y="162" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="start_event">
        <dc:Bounds x="132" y="162" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_0m8gcqy_di" bpmnElement="ad_hoc_subprocess" isExpanded="true">
        <dc:Bounds x="265" y="80" width="350" height="200" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Activity_1p5z08g_di" bpmnElement="user_task_in_ad_hoc_subprocess">
        <dc:Bounds x="380" y="130" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_0em90ai_di" bpmnElement="Flow_0em90ai">
        <di:waypoint x="615" y="180" />
        <di:waypoint x="792" y="180" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_1mi8489_di" bpmnElement="Flow_1mi8489">
        <di:waypoint x="168" y="180" />
        <di:waypoint x="265" y="180" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

const mockProcessInstancesV2 = {
  items: [
    createProcessInstance({
      processInstanceKey: '2251799813685594',
      processDefinitionKey: '2251799813685592',
      processDefinitionId: 'someKey',
      processDefinitionName: 'someProcessName',
      state: 'ACTIVE',
    }),
    createProcessInstance({
      processInstanceKey: '2251799813685596',
      processDefinitionKey: '2251799813685592',
      processDefinitionId: 'someKey',
      processDefinitionName: 'someProcessName',
      state: 'ACTIVE',
      hasIncident: true,
    }),
    createProcessInstance({
      processInstanceKey: '2251799813685598',
      processDefinitionKey: '2251799813685592',
      processDefinitionId: 'someKey',
      processDefinitionName: 'someProcessName',
      state: 'TERMINATED',
      endDate: '2018-06-22',
    }),
  ],
  page: {
    totalItems: 912,
  },
};

export {
  searchResult,
  createIncident,
  createEnhancedIncident,
  mockProcessDefinitions,
  createDiagramNode,
  mockProcessStatistics,
  mockMultipleStatesStatistics,
  mockProcessXML,
  mockProcessWithInputOutputMappingsXML,
  mockCallActivityProcessXML,
  operations,
  multiInstanceProcess,
  eventSubProcess,
  mockProcessInstancesV2,
  createVariable,
  createBatchOperation,
  createUser,
  createProcessInstance,
  createProcessDefinition,
  createInstance,
  adHocSubProcessInnerInstance,
};
