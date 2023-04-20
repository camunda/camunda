/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

// Exports a factory function to create a debounce function that only resolves the last created promise.
// This is used in case we want to debounce a backend request on user input and also want to "cancel"
// the backend request in case of another user input while the backend request is in flight. We cannot
// really cancel a REST request so instead we keep track of the last timeout id that has been created for
// the debounce. If the timeoutId of the promise after the backend request returns is still the last timeout id
// we have stored, no user interaction has been made in between and we can resolve the promise.
export default function debouncePromiseFactory() {
  let lastTimeoutId: NodeJS.Timeout;

  return <T extends (...args: any[]) => any>(
    fct: T,
    delay = 0,
    ...args: any[]
  ): Promise<ReturnType<T>> =>
    new Promise((resolve, reject) => {
      clearTimeout(lastTimeoutId);
      const timeoutId = setTimeout(async () => {
        try {
          const result = await fct(...args);
          if (timeoutId === lastTimeoutId) {
            resolve(result);
          }
        } catch (e) {
          if (timeoutId === lastTimeoutId) {
            reject(e);
          }
        }
      }, delay);
      lastTimeoutId = timeoutId;
    });
}
