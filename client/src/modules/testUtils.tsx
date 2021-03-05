/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {STATE} from 'modules/constants';
import {FlowNodeInstance} from './stores/flowNodeInstance';

/**
 * flushes promises in queue
 */
export const flushPromises = () => {
  return new Promise((resolve) => setImmediate(resolve));
};

/**
 * @returns a jest mock function that resolves with given value
 * @param {*} value to resolve with
 */
export const mockResolvedAsyncFn = (value: any) => {
  return jest.fn(() => Promise.resolve(value));
};

/**
 * @returns a jest mock function that rejects with given value
 * @param {*} value to reject with
 */
export const mockRejectedAsyncFn = (value: any) => {
  return jest.fn(() => Promise.reject(value));
};

/**
 * @returns a jest mock function that rejects with given value
 * @param {*} RootComponent which props can be updated
 * @param {*} ChildComponent which props need to be updated
 * @param {*} updateProps new props
 */
export const setProps = (
  RootComponent: any,
  ChildComponent: any,
  updatedProps: any
) => {
  return RootComponent.setProps({
    children: <ChildComponent {...updatedProps} />,
  });
};

/**
 * @returns a higher order function which executes the wrapped method x times;
 * @param {*} x number of times the method should be executed
 */
export const xTimes = (x: any) => (method: any) => {
  if (x > 0) {
    method(x);
    xTimes(x - 1)(method);
  }
};

const createRandomId = function* createRandomId(type: string) {
  let idx = 0;
  while (true) {
    yield `${type}_${idx}`;
    idx++;
  }
};

const randomIdIterator = createRandomId('id');
const randomActivityIdIterator = createRandomId('activityId');
const randomWorkflowIdInterator = createRandomId('workflowId');
const randomJobIdIterator = createRandomId('jobId');
const eventIdIterator = createRandomId('eventId');
const randomFlowNodeInstanceIdIterator = createRandomId('flowNodeId');
const randomActivityInstanceIdIterator = createRandomId('activityInstanceId');

/**
 * @returns a mocked selection Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createQuery = (options = {}) => {
  return {
    active: true,
    activityId: 'string',
    canceled: true,
    completed: true,
    endDateAfter: '2018-11-13T14:55:58.463Z',
    endDateBefore: '2018-11-13T14:55:58.464Z',
    errorMessage: 'string',
    finished: true,
    incidents: true,
    running: true,
    startDateAfter: '2018-11-13T14:55:58.464Z',
    startDateBefore: '2018-11-13T14:55:58.464Z',
    variablesQuery: {
      name: 'string',
      value: {},
    },
    workflowIds: [],
    ...options,
  };
};

/**
 * @returns a mocked Selection Object with a unique id
 * @param {*} id num value to create unique selection;
 */
export const createSelection = (options = {}) => {
  const instanceId = randomIdIterator.next().value;

  return {
    queries: [createQuery()],
    selectionId: 1,
    totalCount: 1,
    instancesMap: new Map([[instanceId, createInstance({id: instanceId})]]),
    ...options,
  };
};

/**
 * @returns a mocked incident Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createIncident = (options = {}) => {
  return {
    // activityId: createRandomId(),
    // activityInstanceId: createRandomId(),
    errorMessage: 'Some Condition error has occured',
    errorType: 'Condition error',
    id: randomIdIterator.next().value,
    jobId: randomJobIdIterator.next().value,
    state: 'ACTIVE',
    flowNodeId: 'flowNodeId_alwaysFailingTask',
    flowNodeInstanceId: createRandomId('incident'),
    flowNodeName: 'flowNodeName_alwaysFailingTask',
    creationTime: '2019-03-01T14:26:19',
    hasActiveOperation: false,
    ...options,
  };
};

/**
 * @returns a mocked incident Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createOperation = (options = {}): InstanceOperationEntity => {
  return {
    id: 'randomIdIterator.next().value',
    errorMessage: 'string',
    state: 'SENT',
    type: 'RESOLVE_INCIDENT',
    ...options,
  };
};

/**
 * @returns a mocked activity Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createActivity = (options = {}) => {
  return {
    activityId: randomActivityIdIterator.next().value,
    endDate: '2018-10-10T09:20:38.658Z',
    id: randomIdIterator.next().value,
    startDate: '2018-10-10T09:20:38.658Z',
    state: 'ACTIVE',
    ...options,
  };
};

/**
 * @returns a mocked instance Object with a unique id
 * @param {*} customProps Obj with any type of custom property
 */
export const createInstance = (options = {}) => {
  return {
    id: randomIdIterator.next().value,
    workflowId: '2',
    workflowName: 'someWorkflowName',
    workflowVersion: 1,
    startDate: '2018-06-21',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'someKey',
    hasActiveOperation: false,
    operations: [createOperation()],
    sortValues: [],
    ...options,
  } as const;
};

export const createMockInstancesObject = (amount = 5, options = {}) => ({
  workflowInstances: createArrayOfMockInstances(amount),
  totalCount: amount,
  ...options,
});

/**
 * @returns a mocked array of instance objects
 * @param {number} amount specifies the amount of instances
 * @param {object} options to set custom properties for all instances
 */
export const createArrayOfMockInstances = (amount: any, options = {}) => {
  let arrayOfInstances: any = [];
  xTimes(amount)(() =>
    arrayOfInstances.push(
      createInstance({
        id: randomIdIterator.next().value,
        ...options,
      })
    )
  );
  return arrayOfInstances;
};

/**
 * A hard coded object to use when mocking fetchGroupedWorkflows api/instances.js
 */
export const groupedWorkflowsMock = [
  {
    bpmnProcessId: 'demoProcess',
    name: 'New demo process',
    workflows: [
      {
        id: 'demoProcess3',
        name: 'New demo process',
        version: 3,
        bpmnProcessId: 'demoProcess',
      },
      {
        id: 'demoProcess2',
        name: 'Demo process',
        version: 2,
        bpmnProcessId: 'demoProcess',
      },
      {
        id: 'demoProcess1',
        name: 'Demo process',
        version: 1,
        bpmnProcessId: 'demoProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'eventBasedGatewayProcess',
    workflows: [
      {
        id: '2251799813696866',
        name: 'Event based gateway with timer start',
        version: 2,
        bpmnProcessId: 'eventBasedGatewayProcess',
      },
      {
        id: '2251799813685911',
        name: 'Event based gateway with message start',
        version: 1,
        bpmnProcessId: 'eventBasedGatewayProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'bigVarProcess',
    name: 'Big variable process',
    workflows: [
      {
        id: '2251799813685892',
        name: 'Big variable process',
        version: 1,
        bpmnProcessId: 'bigVarProcess',
      },
    ],
  },
  {
    bpmnProcessId: 'orderProcess',
    name: 'Order',
    workflows: [],
  },
];

/**
 * @returns a mocked filter Object with a unique id
 * @param {*} customProps Obj with any type of custom property
 */
export const createFilter = (options = {}) => {
  return {
    workflow: groupedWorkflowsMock[0].bpmnProcessId,
    version: '1',
    active: true,
    ids: '1,2,3',
    startDate: '2018-06-21',
    endDate: '2018-06-22',
    errorMessage: 'No more retries left.',
    incidents: true,
    canceled: true,
    completed: true,
    activityId: randomActivityIdIterator.next().value,
    variable: {name: 'myVariable', value: '123'},
    ...options,
  };
};

/**
 * @returns a mocked workflow Object with a unique id
 * @param {*} customProps Obj with any type of custom property
 */
export const createWorkflow = (options = {}) => {
  return {
    workflowId: randomWorkflowIdInterator.next().value,
    name: 'mockWorkflow',
    version: 1,
    bpmnProcessId: 'mockWorkflow',
    errorMessage: 'JSON path $.paid has no result.',
    instancesWithActiveIncidentsCount: 37,
    activeInstancesCount: 5,
    ...options,
  };
};

/**
 * @returns a single mocked instanceByWorkflow Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createInstanceByWorkflow = (options = {}) => {
  return {
    bpmnProcessId: 'loanProcess',
    workflowName: null,
    instancesWithActiveIncidentsCount: 16,
    activeInstancesCount: 122,
    workflows: [
      createWorkflow({
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
 * @returns a mocked InstancesByWorkflow Object as exposed by 'api/incidents/byWorkflow'
 * @param {*} customProps array with any number of instanceByWorkflow Objects
 */
export const createInstancesByWorkflow = (options: any) => {
  return options || [createInstanceByWorkflow()];
};

/**
 * @returns a single mocked instanceByWorkflow Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createInstanceByError = (options = {}) => {
  return {
    errorMessage: "JSON path '$.paid' has no result.",
    instancesWithErrorCount: 36,
    workflows: [
      createWorkflow({
        workflowId: '1',
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
export const createIncidentsByError = (options: any) => {
  return options || [createInstanceByError()];
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

    $instanceOf: (type: any) => type === 'bpmn:StartEvent',
    ...options,
  };
};

export const createActivities = (diagramNodes: any) => {
  return Object.values(diagramNodes).map((diagramNode) =>
    createActivity({
      // @ts-expect-error ts-migrate(2571) FIXME: Object is of type 'unknown'.
      activityId: diagramNode.id,
    })
  );
};

export const createDefinitions = () => {
  return {
    $type: 'bpmn:Definitions',
    diagrams: ['ModdleElement'],
    exporter: 'Zeebe Modeler',
    exporterVersion: '0.4.0',
    id: 'Definitions_0hir062',
    rootElements: [],
    targetNamespace: '',
  };
};

export const createEvent = (options = {}) => {
  return {
    activityId: 'Task_1b1r7ow',
    activityInstanceId: '1215',
    bpmnProcessId: 'orderProcess',
    dateTime: '2019-01-21T08:34:07.121+0000',
    eventSourceType: 'JOB',
    eventType: 'CREATED',
    id: eventIdIterator.next().value,
    metadata: {
      incidentErrorMessage: null,
      incidentErrorType: null,
      jobCustomHeaders: {},
      jobDeadline: null,
      jobId: '66',
      jobRetries: 3,
      jobType: 'shipArticles',
      jobWorker: '',
      payload: '',
      workflowId: '1',
      workflowInstanceId: '53',
    },
    workflowId: '1',
    workflowInstanceId: '1197',
    ...options,
  };
};

export const createEvents = (activities: any) =>
  activities.map((node: any) =>
    createEvent({
      activityId: node.activityId,
      activityInstanceId: node.id,
      bpmnProcessId: node.activityId,
    })
  );

export const createMetadata = (activityId: any) => ({
  endDate: '--',
  activityInstanceId: activityId,
  jobId: '67',
  startDate: '28 Jan 2019 13:37:46',
  incidentErrorMessage: 'Cannot connect to server delivery05',
  incidentErrorType: 'JOB_NO_RETRIES',
});

// TODO (paddy): remove when legacy FlowNodeInstancesTree is removed
export const createFlowNodeInstance = (options = {}) => {
  return {
    activityId: 'startEvent',
    children: [],
    endDate: '2019-02-07T09:02:34.779+0000',
    id: randomFlowNodeInstanceIdIterator.next().value,
    parentId: '1684',
    isLastChild: false,
    startDate: '2019-02-07T09:02:34.760+0000',
    state: STATE.ACTIVE,
    type: 'bpmn:StartEvent',
    ...options,
  };
};

export const createRawTreeNode = (options = {}) => {
  return {
    activityId: 'Unspecified_1234',
    children: [],
    endDate: '2019-02-07T13:03:36.218Z',
    id: randomActivityInstanceIdIterator.next().value,
    parentId: 'string',
    startDate: '2019-02-07T13:03:36.218Z',
    state: 'ACTIVE',
    type: 'UNSPECIFIED',
    ...options,
  };
};

export const diObject = {set: jest.fn()};

export const createSequenceFlows = () => {
  return [
    {
      id: '2251799813695632',
      workflowInstanceId: '2251799813693731',
      activityId: 'SequenceFlow_0drux68',
    },
    {
      id: '2251799813693749',
      workflowInstanceId: '2251799813693731',
      activityId: 'SequenceFlow_0j6tsnn',
    },
    {
      id: '2251799813695543',
      workflowInstanceId: '2251799813693731',
      activityId: 'SequenceFlow_1dwqvrt',
    },
    {
      id: '2251799813695629',
      workflowInstanceId: '2251799813693731',
      activityId: 'SequenceFlow_1fgekwd',
    },
  ];
};

export const mockWorkflowStatistics = [
  {
    activityId: 'ServiceTask_0kt6c5i',
    active: 1,
    canceled: 0,
    incidents: 0,
    completed: 0,
  },
];

export const mockWorkflowXML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_1771k9d" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Zeebe Modeler" exporterVersion="0.5.0">
  <bpmn:process id="bigVarProcess" isExecutable="true" name="Big variable process">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>SequenceFlow_04ev4jl</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="ServiceTask_0kt6c5i">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="task" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_04ev4jl</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_1njhlr0</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:sequenceFlow id="SequenceFlow_04ev4jl" sourceRef="StartEvent_1" targetRef="ServiceTask_0kt6c5i" />
    <bpmn:endEvent id="EndEvent_0crvjrk">
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

export const mockWorkflowInstances = {
  workflowInstances: [
    {
      id: '2251799813685594',
      workflowId: '2251799813685592',
      workflowName: 'Without Incidents Process',
      workflowVersion: 1,
      startDate: '2020-09-03T15:42:25.107+0000',
      endDate: null,
      state: 'ACTIVE',
      bpmnProcessId: 'withoutIncidentsProcess',
      hasActiveOperation: false,
      operations: [],
    },
    {
      id: '2251799813685596',
      workflowId: '2251799813685592',
      workflowName: 'Without Incidents Process',
      workflowVersion: 1,
      startDate: '2020-09-03T15:42:25.200+0000',
      endDate: null,
      state: 'ACTIVE',
      bpmnProcessId: 'withoutIncidentsProcess',
      hasActiveOperation: false,
      operations: [],
    },
  ],
  totalCount: 912,
};

export const operations = [
  {
    id: '921455fd-849a-49c5-be17-c92eb6d9e946',
    name: null,
    type: 'CANCEL_WORKFLOW_INSTANCE',
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
    type: 'CANCEL_WORKFLOW_INSTANCE',
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
    type: 'CANCEL_WORKFLOW_INSTANCE',
    startDate: '2020-09-29T15:37:16.052+0000',
    endDate: '2020-09-29T15:38:22.227+0000',
    instancesCount: 1,
    operationsTotalCount: 1,
    operationsFinishedCount: 1,
    sortValues: ['1601393902227', '1601393836052'],
  },
];

export const multiInstanceWorkflow = `<?xml version="1.0" encoding="UTF-8"?>
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

export const createMultiInstanceFlowNodeInstances = (
  workflowInstanceId: string
): {
  level1: FlowNodeInstance[];
  level2: FlowNodeInstance[];
  level3: FlowNodeInstance[];
} => {
  return {
    level1: [
      {
        id: '2251799813686130',
        type: 'PARALLEL_GATEWAY',
        state: 'COMPLETED',
        flowNodeId: 'peterFork',
        startDate: '2020-08-18T12:07:33.953+0000',
        endDate: '2020-08-18T12:07:34.034+0000',
        treePath: `${workflowInstanceId}/2251799813686130`,
        sortValues: [1606300828415, '2251799813686130'],
      },
      {
        id: '2251799813686156',
        type: 'MULTI_INSTANCE_BODY',
        state: 'INCIDENT',
        flowNodeId: 'filterMapSubProcess',
        startDate: '2020-08-18T12:07:34.205+0000',
        endDate: null,
        treePath: `${workflowInstanceId}/2251799813686156`,
        sortValues: [1606300828415, '2251799813686156'],
      },
    ],
    level2: [
      {
        id: '2251799813686166',
        type: 'SUB_PROCESS',
        state: 'INCIDENT',
        flowNodeId: 'filterMapSubProcess',
        startDate: '2020-08-18T12:07:34.281+0000',
        endDate: null,
        treePath: `${workflowInstanceId}/2251799813686156/2251799813686166`,
        sortValues: [1606300828415, '2251799813686166'],
      },
    ],
    level3: [
      {
        id: '2251799813686204',
        type: 'START_EVENT',
        state: 'COMPLETED',
        flowNodeId: 'startFilterMap',
        startDate: '2020-08-18T12:07:34.337+0000',
        endDate: '2020-08-18T12:07:34.445+0000',
        treePath: `${workflowInstanceId}/2251799813686156/2251799813686166/2251799813686204`,
        sortValues: [1606300828415, '2251799813686204'],
      },
    ],
  };
};
