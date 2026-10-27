/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// @ts-expect-error
export * from 'react-18';
// @ts-expect-error
export {default} from 'react-18';

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
