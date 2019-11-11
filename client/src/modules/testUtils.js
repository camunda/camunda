/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE} from 'modules/constants';

/**
 * flushes promises in queue
 */
export const flushPromises = () => {
  return new Promise(resolve => setImmediate(resolve));
};

/**
 * @returns a jest mock function that resolves with given value
 * @param {*} value to resolve with
 */
export const mockResolvedAsyncFn = value => {
  return jest.fn(() => Promise.resolve(value));
};

/**
 * @returns a jest mock function that rejects with given value
 * @param {*} value to reject with
 */
export const mockRejectedAsyncFn = value => {
  return jest.fn(() => Promise.reject(value));
};

/**
 * @returns a higher order function which executes the wrapped method x times;
 * @param {*} x number of times the method should be executed
 */
export const xTimes = x => method => {
  if (x > 0) {
    method(x);
    xTimes(x - 1)(method);
  }
};

const createRandomId = function* createRandomId(type) {
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
    excludeIds: [],
    finished: true,
    ids: [],
    incidents: true,
    running: true,
    startDateAfter: '2018-11-13T14:55:58.464Z',
    startDateBefore: '2018-11-13T14:55:58.464Z',
    variablesQuery: {
      name: 'string',
      value: {}
    },
    workflowIds: [],
    ...options
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
    ...options
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
    flowNodeInstanceId: createRandomId(),
    flowNodeName: 'flowNodeName_alwaysFailingTask',
    creationTime: '2019-03-01T14:26:19',
    hasActiveOperation: false,
    ...options
  };
};

/**
 * @returns a mocked incident Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createOperation = (options = {}) => {
  return {
    endDate: '2018-10-10T09:20:38.661Z',
    errorMessage: 'string',
    startDate: '2018-10-10T09:20:38.661Z',
    state: 'SCHEDULED',
    type: 'RESOLVE_INCIDENT',
    ...options
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
    ...options
  };
};

/**
 * @returns a mocked instance Object with a unique id
 * @param {*} customProps Obj with any type of custom property
 */
export const createInstance = (options = {}) => {
  return {
    activities: [createActivity()],
    bpmnProcessId: 'someKey',
    endDate: null,
    id: randomIdIterator.next().value,
    operations: [createOperation()],
    sequenceFlows: [],
    startDate: '2018-06-21',
    state: 'ACTIVE',
    workflowId: '2',
    workflowName: 'someWorkflowName',
    workflowVersion: 1,
    ...options
  };
};

export const createMockInstancesObject = (amount = 5, options = {}) => ({
  workflowInstances: createArrayOfMockInstances(amount),
  totalCount: amount,
  ...options
});

/**
 * @returns a mocked array of instance objects
 * @param {number} amount specifies the amount of instances
 * @param {object} options to set custom properties for all instances
 */
export const createArrayOfMockInstances = (amount, options = {}) => {
  let arrayOfInstances = [];
  xTimes(amount)(() =>
    arrayOfInstances.push(
      createInstance({
        id: randomIdIterator.next().value,
        ...options
      })
    )
  );
  return arrayOfInstances;
};

/**
 * @returns a Map with mocked instanceId 'keys' and instance object 'values'
 * @param {Array} arrayofInstances contains any number of instance objects
 */
export const createMapOfMockInstances = arrayofInstances => {
  const transformedInstances = arrayofInstances.reduce((acc, instance) => {
    return {
      ...acc,
      [instance.id]: instance
    };
  }, {});
  return new Map(Object.entries(transformedInstances));
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
        id: '6',
        name: 'New demo process',
        version: 3,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '4',
        name: 'Demo process',
        version: 2,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '1',
        name: 'Demo process',
        version: 1,
        bpmnProcessId: 'demoProcess'
      }
    ]
  },
  {
    bpmnProcessId: 'orderProcess',
    name: 'Order',
    workflows: []
  }
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
    ...options
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
    ...options
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
        activeInstancesCount: 122
      })
    ],
    ...options
  };
};

/**
 * @returns a mocked InstancesByWorkflow Object as exposed by 'api/incidents/byWorkflow'
 * @param {*} customProps array with any number of instanceByWorkflow Objects
 */
export const createInstancesByWorkflow = options => {
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
        activeInstancesCount: null
      })
    ],
    ...options
  };
};

/**
 * @returns a mocked InstancesByError Object as exposed by 'api/incidents/byError'
 * @param {*} customProps array with any number of instanceByError Objects
 */
export const createIncidentsByError = options => {
  return options || [createInstanceByError()];
};

/**
 * @returns mocked Core Statistis Object as exposed by '/api/workflow-instances/core-statistics'
 * @param {*} customProps array with any number of instanceByError Objects
 */
export const createCoreStatistics = (options = {}) => {
  return {
    running: 10,
    active: 7,
    withIncident: 3,
    ...options
  };
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

    $instanceOf: type => type === 'bpmn:StartEvent',
    ...options
  };
};

export const createActivities = diagramNodes => {
  return Object.values(diagramNodes).map(diagramNode =>
    createActivity({
      activityId: diagramNode.id
    })
  );
};

export const createDiagramStatistics = () => {
  return [
    {
      activityId: 'afterTimerTask',
      active: 0,
      canceled: 0,
      incidents: 8,
      completed: 0
    },
    {
      activityId: 'lastTask',
      active: 0,
      canceled: 0,
      incidents: 21,
      completed: 0
    }
  ];
};

export const createDefinitions = () => {
  return {
    $type: 'bpmn:Definitions',
    diagrams: ['ModdleElement'],
    exporter: 'Zeebe Modeler',
    exporterVersion: '0.4.0',
    id: 'Definitions_0hir062',
    rootElements: [],
    targetNamespace: ''
  };
};

export const createEvent = (options = {}) => {
  return {
    activityId: 'Task_1b1r7ow',
    activityInstanceId: '1215',
    bpmnProcessId: 'orderProcess',
    dateTime: '2019-01-21T08:34:07.121+0000',
    eventSourceTyppe: 'JOB',
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
      workflowInstanceId: '53'
    },
    workflowId: '1',
    workflowInstanceId: '1197',
    ...options
  };
};

export const createEvents = activities =>
  activities.map(node =>
    createEvent({
      activityId: node.activityId,
      activityInstanceId: node.id,
      bpmnProcessId: node.activityId
    })
  );

export const createMetadata = activityId => ({
  endDate: '--',
  activityInstanceId: activityId,
  jobId: '67',
  startDate: '28 Jan 2019 13:37:46',
  incidentErrorMessage: 'Cannot connect to server delivery05',
  incidentErrorType: 'JOB_NO_RETRIES'
});

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
    ...options
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
    ...options
  };
};

export const createVariables = () => {
  return [
    {
      id: '1031-clientNo',
      name: 'clientNo',
      value: '"CNT-1211132-02"',
      scopeId: '1031',
      workflowInstanceId: '1031'
    },
    {
      id: '1031-items',
      name: 'items',
      value:
        '[{"code":"123.135.625","name":"Laptop Lenovo ABC-001","quantity":1,"price":488.0},{"code":"111.653.365","name":"Headset Sony QWE-23","quantity":2,"price":72.0}]',
      scopeId: '1031',
      workflowInstanceId: '1031'
    },
    {
      id: '1031-mwst',
      name: 'mwst',
      value: '106.4',
      scopeId: '1031',
      workflowInstanceId: '1031'
    }
  ];
};
