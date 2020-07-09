/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export * from 'react';
export {default} from 'react';

const outstandingEffects = [];

export const useEffect = (fn) => outstandingEffects.push(fn);
export const runLastEffect = () => {
  if (outstandingEffects.length) {
    outstandingEffects.pop()();
  }
};

export const runAllEffects = () => {
  // Effects can cause scheduling of other effects. To prevent infinite loops,
  // we only run effects that are in the queue when runAllEffects was called.
  const numberOfEffects = outstandingEffects.length;

  for (let i = 0; i < numberOfEffects; i++) {
    outstandingEffects.shift()();
  }
};
