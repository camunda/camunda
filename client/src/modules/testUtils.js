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
const randomJobIdIterator = createRandomId('jobId');

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
    activityId: createRandomId(),
    activityInstanceId: createRandomId(),
    errorMessage: '',
    errorType: '',
    id: randomIdIterator.next().value,
    jobId: randomJobIdIterator.next().value,
    state: 'ACTIVE',
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
    type: 'UPDATE_RETRIES',
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
    incidents: [createIncident()],
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

/**
 * @returns a mocked filter Object with a unique id
 * @param {*} customProps Obj with any type of custom property
 */
export const createFilter = (options = {}) => {
  return {
    workflow: 'mockWorkflow',
    version: '1',
    active: true,
    ids: '1,2,3',
    startDate: '2018-06-21',
    endDate: '2018-06-22',
    errorMessage: 'mock error message',
    incidents: true,
    canceled: true,
    completed: true,
    activityId: randomActivityIdIterator.next().value,
    ...options
  };
};

/**
 * @returns a mocked workflow Object with a unique id
 * @param {*} customProps Obj with any type of custom property
 */
export const createWorkflow = (options = {}) => {
  return {
    id: '1',
    name: 'mockWorkflow',
    version: 1,
    bpmnProcessId: 'mockWorkflow',
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
    name: 'Some Mock Name',
    type: 'bpmn:StartEvent',
    ...options
  };
};

/**
 * @returns a mocked diagramNode Object with a unique id
 * @param {*} customProps Obj with any type of custom property
 */
export const createGroupedWorkflow = (options = {}) => {
  return {
    bpmnProcessId: 'mockWorkflow',
    name: 'mockWorkflow Name',
    workflows: [createWorkflow()]
  };
};

/**
 * @returns a mocked groupedWorkflowInstances Object with a unique id
 * @param {*} customProps Obj with any type of custom property
 */
export const createGroupedWorkflowInstances = (options = {}) => {
  return {
    mockWorkflowOne: createWorkflow({name: 'mockWorkflowOne'}),
    mockWorkflowTwo: createGroupedWorkflow({name: 'mockWorkflowTwo'}),
    ...options
  };
};

export const getDiagramNodes = () => {
  return {
    taskD: {
      $type: 'bpmn:ServiceTask',
      id: 'taskD',
      name: 'task D'
    },
    demoProcess: {
      $type: 'bpmn:Process',
      id: 'demoProcess',
      isExecutable: true,
      name: 'Demo process'
    }
  };
};
