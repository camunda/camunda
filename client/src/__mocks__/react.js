/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

export * from 'react';
export {default} from 'react';

const outstandingEffects = [];
const outstandingCleanups = [];

export const useEffect = (fn) => outstandingEffects.push(fn);
export const runLastEffect = () => {
  if (outstandingEffects.length) {
    const cleanup = outstandingEffects.pop()();

    if (cleanup) {
      outstandingCleanups.push(cleanup);
    }
  }
};

export const runLastCleanup = () => {
  if (outstandingCleanups.length) {
    outstandingCleanups.pop()();
  }
};

export const runAllEffects = () => {
  // Effects can cause scheduling of other effects. To prevent infinite loops,
  // we only run effects that are in the queue when runAllEffects was called.
  const numberOfEffects = outstandingEffects.length;

  for (let i = 0; i < numberOfEffects; i++) {
    const cleanup = outstandingEffects.shift()();

    if (cleanup) {
      outstandingCleanups.push(cleanup);
    }
  }
};

beforeEach(() => {
  outstandingEffects.length = 0;
});

export const runAllCleanups = () => {
  const numberOfEffects = outstandingCleanups.length;

  for (let i = 0; i < numberOfEffects; i++) {
    outstandingCleanups.shift()();
  }
};
