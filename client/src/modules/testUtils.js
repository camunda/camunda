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

const createRandId = () => {
  return Math.random()
    .toString(36)
    .replace(/[^a-z]+/g, '')
    .substr(2, 10);
};

/**
 * @returns a mocked incident Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createIncident = ({
  activityId = createRandId(),
  activityInstanceId = createRandId(),
  errorMessage = '',
  errorType = '',
  id = createRandId(),
  jobId = createRandId(),
  state = 'ACTIVE'
} = {}) => {
  return {
    activityId,
    activityInstanceId,
    errorMessage,
    errorType,
    id,
    jobId,
    state
  };
};

/**
 * @returns a mocked incident Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createOperation = ({
  endDate = '2018-10-10T09:20:38.661Z',
  errorMessage = 'string',
  startDate = '2018-10-10T09:20:38.661Z',
  state = 'SCHEDULED',
  type = 'UPDATE_RETRIES'
} = {}) => {
  return {
    endDate,
    errorMessage,
    startDate,
    state,
    type
  };
};

/**
 * @returns a mocked activity Object
 * @param {*} customProps Obj with any type of custom property
 */
export const createActivity = ({
  activityId = createRandId(),
  endDate = '2018-10-10T09:20:38.658Z',
  id = createRandId(),
  startDate = '2018-10-10T09:20:38.658Z',
  state = 'ACTIVE'
} = {}) => {
  return {
    activityId,
    endDate,
    id,
    startDate,
    state
  };
};

/**
 * @returns a mocked instance Object with a unique id
 * @param {*} id num value to create unique instance;
 */
export const createInstance = ({
  activities = [createActivity()],
  businessKey = 'someKey',
  endDate = null,
  id = createRandId(),
  incidents = [createIncident()],
  operations = [createOperation()],
  startDate = '2018-06-21T11:13:31.094+0000',
  state = 'ACTIVE',
  workflowId = '2'
} = {}) => {
  return {
    activities,
    businessKey,
    endDate,
    id,
    incidents,
    operations,
    startDate,
    state,
    workflowId
  };
};
