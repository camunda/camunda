/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import debouncePromiseFactory from 'debouncePromise';

jest.useFakeTimers();

it('should return a function that can be used with a function', () => {
  const debouncePromise = debouncePromiseFactory();
  const actualFunction = jest.fn();

  debouncePromise(actualFunction);

  jest.runAllTimers();

  expect(actualFunction).toHaveBeenCalled();
});

it('should not run the provided function until the specified timeout', () => {
  const debouncePromise = debouncePromiseFactory();
  const actualFunction = jest.fn();

  debouncePromise(actualFunction, 300);

  jest.advanceTimersByTime(200);

  expect(actualFunction).not.toHaveBeenCalled();

  jest.advanceTimersByTime(200);

  expect(actualFunction).toHaveBeenCalled();
});

it('should only run the last function call withing the specified timeout', () => {
  const debouncePromise = debouncePromiseFactory();
  const f1 = jest.fn();
  const f2 = jest.fn();
  const f3 = jest.fn();

  debouncePromise(f1, 300);
  jest.advanceTimersByTime(100);
  debouncePromise(f2, 300);
  jest.advanceTimersByTime(100);
  debouncePromise(f3, 300);
  jest.advanceTimersByTime(200);

  expect(f1).not.toHaveBeenCalled();
  expect(f2).not.toHaveBeenCalled();
  expect(f3).not.toHaveBeenCalled();

  jest.advanceTimersByTime(200);

  expect(f1).not.toHaveBeenCalled();
  expect(f2).not.toHaveBeenCalled();
  expect(f3).toHaveBeenCalled();
});

it('should not resolve a promise from a function that was called if another call came in while the function was running', async () => {
  const debouncePromise = debouncePromiseFactory();

  const spy = jest.fn();
  const f1 = jest
    .fn()
    .mockImplementation(() => new Promise((resolve) => setTimeout(() => resolve(1), 100)));
  const f2 = jest
    .fn()
    .mockImplementation(() => new Promise((resolve) => setTimeout(() => resolve(2), 100)));

  async function caller(fn: any) {
    const result = await debouncePromise(fn, 300);
    spy(result);
  }

  caller(f1);
  // at this point, we called debouncePromise with f1 and the 300ms countdown until the backend requests starts

  jest.advanceTimersByTime(350);
  await flushPromises();
  expect(f1).toHaveBeenCalledTimes(1);
  // at this point, the backend request of f1 is in flight

  caller(f2);
  // we debouncePromise with f2, resetting the 300 ms countdown

  jest.advanceTimersByTime(100);
  await flushPromises();
  expect(spy).not.toHaveBeenCalled();
  expect(f2).not.toHaveBeenCalled();
  // at this point, the backend request from f1 returned, but as we called debouncePromise with f2 in the meantime, the backend response should have been discarded

  jest.advanceTimersByTime(250);
  await flushPromises();
  expect(f2).toHaveBeenCalled();
  expect(spy).not.toHaveBeenCalled();
  // at this point, the backend request for f2 is fired

  jest.advanceTimersByTime(100);
  await flushPromises();
  expect(spy).toHaveBeenCalledTimes(1);
  expect(spy).toHaveBeenCalledWith(2);
  // at this point, both backend requests should have returned, but only the second one has been applied
});
