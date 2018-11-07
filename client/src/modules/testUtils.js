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

/**
 * @returns a mocked Selection Object with a unique id
 * @param {*} id num value to create unique selection;
 */
export const createSelection = id => {
  return {
    queries: [],
    selectionId: id,
    totalCount: 1,
    workflowInstances: []
  };
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
 * @param {*} id num value to create unique instance;
 */
export const createInstance = (options = {}) => {
  return {
    activities: [createActivity()],
    bpmnProcessId: 'someKey',
    id: randomIdIterator.next().value,
    incidents: [createIncident()],
    endDate: null,
    operations: [createOperation()],
    startDate: '2018-06-21',
    state: 'ACTIVE',
    workflowId: '2',
    workflowVersion: 1,
    workflowName: 'someWorkflowName',
    ...options
  };
};
