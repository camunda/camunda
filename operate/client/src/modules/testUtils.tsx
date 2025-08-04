/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {FlowNodeInstances} from 'modules/stores/flowNodeInstance';
import type {IncidentByErrorDto} from './api/incidents/fetchIncidentsByError';
import type {ProcessInstanceByNameDto} from './api/incidents/fetchProcessInstancesByName';
import type {ProcessDto} from './api/processes/fetchGroupedProcesses';
import type {IncidentDto} from './api/processInstances/fetchProcessInstanceIncidents';
import type {BatchOperationDto} from './api/sharedTypes';
import type {
  ProcessInstance,
  Variable,
  CurrentUser,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import type {
  ProcessInstanceEntity,
  VariableEntity,
  OperationEntity,
  InstanceOperationEntity,
} from 'modules/types/operate';

const createRandomId = function* createRandomId(type: string) {
  let idx = 0;
  while (true) {
    yield `${type}_${idx}`;
    idx++;
  }
};

const randomIdIterator = createRandomId('id');
const randomProcessIdIterator = createRandomId('processId');
const randomJobIdIterator = createRandomId('jobId');
const randomFlowNodeInstanceIdIterator = createRandomId('flowNodeInstance');

/**
 * @returns a mocked incident Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createIncident = (
  options: Partial<IncidentDto> = {},
): IncidentDto => {
  return {
    errorMessage: 'Some Condition error has occurred',
    errorType: {
      name: 'Condition error',
      id: 'CONDITION_ERROR',
    },
    id: randomIdIterator.next().value,
    jobId: randomJobIdIterator.next().value,
    flowNodeId: 'flowNodeId_alwaysFailingTask',
    flowNodeInstanceId: randomFlowNodeInstanceIdIterator.next().value,
    creationTime: '2019-03-01T14:26:19',
    hasActiveOperation: false,
    lastOperation: null,
    rootCauseInstance: null,
    ...options,
  };
};

/**
 * @returns a mocked incident Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createOperation = (
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
export const createInstance = (
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

const createVariable = (
  options: Partial<VariableEntity> = {},
): VariableEntity => {
  const name = options.name ?? 'testVariableName';
  return {
    id: `2251799813725337-${name}`,
    name,
    value: '1',
    isPreview: false,
    hasActiveOperation: false,
    isFirst: false,
    sortValues: [name],
    ...options,
  };
};

const createVariableV2 = (options: Partial<Variable> = {}): Variable => {
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
  authorizedApplications: [],
  tenants: [],
  groups: [],
  roles: [],
  salesPlanType: null,
  c8Links: [],
  canLogout: true,
  apiUser: false,
  ...options,
});

/**
 * A hard coded object to use when mocking fetchGroupedProcesses api/instances.js
 */
export const groupedProcessesMock: ProcessDto[] = [
  {
    bpmnProcessId: 'demoProcess',
    name: 'New demo process',
    tenantId: '<default>',
    processes: [
      {
        id: 'demoProcess3',
        name: 'New demo process',
        version: 3,
        bpmnProcessId: 'demoProcess',
        versionTag: null,
      },
      {
        id: 'demoProcess2',
        name: 'Demo process',
        version: 2,
        bpmnProcessId: 'demoProcess',
        versionTag: null,
      },
      {
        id: 'demoProcess1',
        name: 'Demo process',
        version: 1,
        bpmnProcessId: 'demoProcess',
        versionTag: null,
      },
    ],
    permissions: ['UPDATE_PROCESS_INSTANCE'],
  },
  {
    bpmnProcessId: 'eventBasedGatewayProcess',
    name: null,
    tenantId: '<default>',
    processes: [
      {
        id: '2251799813696866',
        name: 'Event based gateway with timer start',
        version: 2,
        bpmnProcessId: 'eventBasedGatewayProcess',
        versionTag: null,
      },
      {
        id: '2251799813685911',
        name: 'Event based gateway with message start',
        version: 1,
        bpmnProcessId: 'eventBasedGatewayProcess',
        versionTag: null,
      },
    ],
    permissions: ['DELETE'],
  },
  {
    bpmnProcessId: 'bigVarProcess',
    name: 'Big variable process',
    tenantId: '<default>',
    processes: [
      {
        id: '2251799813685892',
        name: 'Big variable process',
        version: 1,
        bpmnProcessId: 'bigVarProcess',
        versionTag: 'MyVersionTag',
      },
    ],
    permissions: ['DELETE_PROCESS_INSTANCE'],
  },
  {
    bpmnProcessId: 'bigVarProcess',
    name: 'Big variable process',
    tenantId: '<tenant-A>',
    processes: [
      {
        id: '2251799813685893',
        name: 'Big variable process',
        version: 2,
        bpmnProcessId: 'bigVarProcess',
        versionTag: null,
      },
      {
        id: '2251799813685894',
        name: 'Big variable process',
        version: 1,
        bpmnProcessId: 'bigVarProcess',
        versionTag: null,
      },
    ],
    permissions: ['DELETE_PROCESS_INSTANCE'],
  },
  {
    bpmnProcessId: 'orderProcess',
    tenantId: '<default>',
    name: 'Order',
    processes: [],
  },
];

/**
 * @returns a mocked process Object with a unique id
 * @param {*} customProps Obj with any type of custom property
 */
export const createProcess = (options = {}) => {
  return {
    processId: randomProcessIdIterator.next().value,
    tenantId: '<default>',
    name: 'mockProcess',
    version: 1,
    bpmnProcessId: 'mockProcess',
    errorMessage: 'JSON path $.paid has no result.',
    instancesWithActiveIncidentsCount: 37,
    activeInstancesCount: 5,
    ...options,
  };
};

/**
 * @returns a single mocked instanceByProcess Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createInstanceByProcess = (
  options: Partial<ProcessInstanceByNameDto> = {},
): ProcessInstanceByNameDto => {
  return {
    bpmnProcessId: 'loanProcess',
    tenantId: '<default>',
    processName: null,
    instancesWithActiveIncidentsCount: 16,
    activeInstancesCount: 122,
    processes: [
      createProcess({
        name: null,
        bpmnProcessId: 'loanProcess',
        instancesWithActiveIncidentsCount: 16,
        activeInstancesCount: 122,
      }),
    ],
    ...options,
  };
};

/**
 * @returns a single mocked instanceByProcess Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createIncidentByError = (
  options: Partial<IncidentByErrorDto> = {},
): IncidentByErrorDto => {
  return {
    errorMessage: "JSON path '$.paid' has no result.",
    incidentErrorHashCode: 234254,
    instancesWithErrorCount: 36,
    processes: [
      createProcess({
        processId: '1',
        version: 1,
        name: 'Order process',
        bpmnProcessId: 'orderProcess',
        errorMessage: "JSON path '$.paid' has no result.",
        instancesWithActiveIncidentsCount: 36,
        activeInstancesCount: null,
      }),
    ],
    ...options,
  };
};

/**
 * @returns a mocked InstancesByError Object as exposed by 'api/incidents/byError'
 * @param {*} customProps array with any number of instanceByError Objects
 */
export const createIncidentsByError = (options: IncidentByErrorDto[]) => {
  return options || [createIncidentByError()];
};

/**
 * @returns a mocked diagramNode Object with a unique id
 * @param {*} customProps Obj with any type of custom property
 */
export const createDiagramNode = (options = {}) => {
  return {
    id: 'StartEvent_1',
    name: 'Start Event',
    $type: 'bpmn:StartEvent',

    $instanceOf: (type: string) => type === 'bpmn:StartEvent',
    ...options,
  };
};

export const createSequenceFlows = () => {
  return [
    {
      processInstanceId: '2251799813693731',
      activityId: 'SequenceFlow_0drux68',
    },
    {
      processInstanceId: '2251799813693731',
      activityId: 'SequenceFlow_0j6tsnn',
    },
    {
      processInstanceId: '2251799813693731',
      activityId: 'SequenceFlow_1dwqvrt',
    },
    {
      processInstanceId: '2251799813693731',
      activityId: 'SequenceFlow_1fgekwd',
    },
  ];
};

export const mockProcessStatisticsV2 = {
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

export const mockMultipleStatesStatistics = {
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

export const mockProcessXML = `<?xml version="1.0" encoding="UTF-8"?>
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

export const mockProcessWithInputOutputMappingsXML = `<?xml version="1.0" encoding="UTF-8"?><bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:modeler="http://camunda.org/schema/modeler/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:camunda="http://camunda.org/schema/1.0/bpmn" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Web Modeler" exporterVersion="eb9fa7e" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.0.0" camunda:diagramRelationId="9ee67cec-c2eb-4b0d-968b-f7a9ae3d6d3d">
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

export const mockCallActivityProcessXML = `<?xml version="1.0" encoding="UTF-8"?>
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

export const mockProcessInstances = {
  processInstances: [
    createInstance({id: '2251799813685594', processId: '2251799813685592'}),
    createInstance({
      id: '2251799813685596',
      processId: '2251799813685592',
      state: 'INCIDENT',
    }),
    createInstance({
      id: '2251799813685598',
      processId: '2251799813685592',
      state: 'CANCELED',
    }),
  ],
  totalCount: 912,
};

export const mockProcessInstancesWithOperation = {
  processInstances: [
    createInstance({
      id: '0000000000000002',
      processId: '2251799813685612',
      state: 'ACTIVE',
      operations: [
        {
          state: 'FAILED',
          batchOperationId: 'f4be6304-a0e0-4976-b81b-7a07fb4e96e5',
          errorMessage: 'Batch Operation Error Message',
          type: 'MODIFY_PROCESS_INSTANCE',
          completedDate: null,
        },
        {
          state: 'COMPLETED',
          batchOperationId: 'c4be6304-a0e0-4976-b81b-7a07fb4e96e5',
          errorMessage: '',
          type: 'MODIFY_PROCESS_INSTANCE',
          completedDate: null,
        },
      ],
    }),
  ],
  totalCount: 1,
};

export const mockCalledProcessInstances = {
  processInstances: [
    createInstance({
      id: '2251799813685837',
      processId: '2251799813685592',
      parentInstanceId: '22517998136837261',
    }),
  ],
  totalCount: 1,
};

export const operations: OperationEntity[] = [
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

export const multiInstanceProcess = `<?xml version="1.0" encoding="UTF-8"?>
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

export const eventSubProcess = `<?xml version="1.0" encoding="UTF-8"?>
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

export const createMultiInstanceFlowNodeInstances = (
  processInstanceId: string,
): {
  level1: FlowNodeInstances;
  level1Next: FlowNodeInstances;
  level1Prev: FlowNodeInstances;
  level1Poll: FlowNodeInstances;
  level2: FlowNodeInstances;
  level3: FlowNodeInstances;
} => {
  return {
    level1: {
      [processInstanceId]: {
        running: null,
        children: [
          {
            id: '2251799813686130',
            type: 'PARALLEL_GATEWAY',
            state: 'COMPLETED',
            flowNodeId: 'peterFork',
            startDate: '2020-08-18T12:07:33.953+0000',
            endDate: '2020-08-18T12:07:34.034+0000',
            treePath: `${processInstanceId}/2251799813686130`,
            sortValues: ['1606300828415', '2251799813686130'],
          },
          {
            id: '2251799813686156',
            type: 'MULTI_INSTANCE_BODY',
            state: 'INCIDENT',
            flowNodeId: 'filterMapSubProcess',
            startDate: '2020-08-18T12:07:34.205+0000',
            endDate: null,
            treePath: `${processInstanceId}/2251799813686156`,
            sortValues: ['1606300828415', '2251799813686156'],
          },
        ],
      },
    },
    level1Next: {
      [processInstanceId]: {
        running: null,
        children: [
          {
            id: '2251799813686472',
            type: 'PARALLEL_GATEWAY',
            state: 'INCIDENT',
            flowNodeId: 'filterMapSubProcess',
            startDate: '2020-08-18T12:08:00.205+0000',
            endDate: null,
            treePath: `${processInstanceId}/2251799813686472`,
            sortValues: ['1606300828415', '2251799813686472'],
          },
        ],
      },
    },
    level1Prev: {
      [processInstanceId]: {
        running: null,
        children: [
          {
            id: '2251390423657139',
            type: 'START_EVENT',
            state: 'INCIDENT',
            flowNodeId: 'startFilterMap',
            startDate: '2020-08-18T12:08:00.205+0000',
            endDate: null,
            treePath: `${processInstanceId}/2251390423657139`,
            sortValues: ['1606300828415', '2251390423657139'],
          },
        ],
      },
    },
    level1Poll: {
      [processInstanceId]: {
        running: null,
        children: [
          {
            id: '2251799813686130',
            type: 'PARALLEL_GATEWAY',
            state: 'COMPLETED',
            flowNodeId: 'peterFork',
            startDate: '2020-08-18T12:07:33.953+0000',
            endDate: '2020-08-18T12:07:34.034+0000',
            treePath: `${processInstanceId}/2251799813686130`,
            sortValues: ['1606300828415', '2251799813686130'],
          },
          {
            id: '2251799813686156',
            type: 'MULTI_INSTANCE_BODY',
            state: 'COMPLETED',
            flowNodeId: 'filterMapSubProcess',
            startDate: '2020-08-18T12:07:34.205+0000',
            endDate: '2020-08-18T12:07:34.034+0000',
            treePath: `${processInstanceId}/2251799813686156`,
            sortValues: ['1606300828415', '2251799813686156'],
          },
        ],
      },
    },
    level2: {
      [`${processInstanceId}/2251799813686156`]: {
        running: true,
        children: [
          {
            id: '2251799813686166',
            type: 'SUB_PROCESS',
            state: 'INCIDENT',
            flowNodeId: 'filterMapSubProcess',
            startDate: '2020-08-18T12:07:34.281+0000',
            endDate: null,
            treePath: `${processInstanceId}/2251799813686156/2251799813686166`,
            sortValues: ['1606300828415', '2251799813686166'],
          },
        ],
      },
    },
    level3: {
      [`${processInstanceId}/2251799813686156/2251799813686166`]: {
        running: false,
        children: [
          {
            id: '2251799813686204',
            type: 'START_EVENT',
            state: 'COMPLETED',
            flowNodeId: 'startFilterMap',
            startDate: '2020-08-18T12:07:34.337+0000',
            endDate: '2020-08-18T12:07:34.445+0000',
            treePath: `${processInstanceId}/2251799813686156/2251799813686166/2251799813686204`,
            sortValues: ['1606300828415', '2251799813686204'],
          },
        ],
      },
    },
  };
};

export const createEventSubProcessFlowNodeInstances = (
  processInstanceId: string,
): {
  level1: FlowNodeInstances;
  level2: FlowNodeInstances;
} => {
  return {
    level1: {
      [processInstanceId]: {
        children: [
          {
            id: '6755399441057427',
            type: 'START_EVENT',
            state: 'COMPLETED',
            flowNodeId: 'StartEvent_1vnazga',
            startDate: '2021-06-22T13:43:59.698+0000',
            endDate: '2021-06-22T13:43:59.701+0000',
            treePath: `${processInstanceId}/6755399441057427`,
            sortValues: ['1624369439698', '6755399441057427'],
          },
          {
            id: '6755399441057429',
            type: 'SERVICE_TASK',
            state: 'TERMINATED',
            flowNodeId: 'ServiceTask_1daop2o',
            startDate: '2021-06-22T13:43:59.707+0000',
            endDate: '2021-06-22T13:46:59.705+0000',
            treePath: `${processInstanceId}/6755399441057429`,
            sortValues: ['1624369439707', '6755399441057429'],
          },
          {
            id: '6755399441063916',
            type: 'EVENT_SUB_PROCESS',
            state: 'INCIDENT',
            flowNodeId: 'SubProcess_1ip6c6s',
            startDate: '2021-06-22T13:46:59.705+0000',
            endDate: null,
            treePath: `${processInstanceId}/6755399441063916`,
            sortValues: ['1624369619705', '6755399441063916'],
          },
        ],
        running: null,
      },
    },
    level2: {
      [`${processInstanceId}/6755399441063916`]: {
        children: [
          {
            id: '6755399441063918',
            type: 'START_EVENT',
            state: 'COMPLETED',
            flowNodeId: 'StartEvent_1u9mwoj',
            startDate: '2021-06-22T13:46:59.714+0000',
            endDate: '2021-06-22T13:46:59.719+0000',
            treePath: `${processInstanceId}/6755399441063916/6755399441063918`,
            sortValues: ['1624369619714', '6755399441063918'],
          },
          {
            id: '6755399441063920',
            type: 'SERVICE_TASK',
            state: 'INCIDENT',
            flowNodeId: 'ServiceTask_0h8cwwl',
            startDate: '2021-06-22T13:46:59.722+0000',
            endDate: null,
            treePath: `${processInstanceId}/6755399441063916/6755399441063920`,
            sortValues: ['1624369619722', '6755399441063920'],
          },
        ],
        running: true,
      },
    },
  };
};

export {
  createVariable,
  createVariableV2,
  createBatchOperation,
  createUser,
  createProcessInstance,
};
