/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

// @ts-ignore
export * from 'react';
export {default} from 'react';

const outstandingEffects: (() => void)[] = [];
const outstandingCleanups: (() => void)[] = [];

export const useEffect = (fn: () => (() => void) | void) => outstandingEffects.push(fn);
export const runLastEffect = () => {
  if (outstandingEffects.length) {
    const cleanup = outstandingEffects.pop()?.();

    if (cleanup) {
      outstandingCleanups.push(cleanup);
    }
  }
};

export const runLastCleanup = () => {
  if (outstandingCleanups.length) {
    outstandingCleanups.pop()?.();
  }
};

export const runAllEffects = () => {
  const numberOfEffects = outstandingEffects.length;

  for (let i = 0; i < numberOfEffects; i++) {
    const cleanup = outstandingEffects.shift()?.();

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
    outstandingCleanups.shift()?.();
  }
};
