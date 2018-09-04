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
