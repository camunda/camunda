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
