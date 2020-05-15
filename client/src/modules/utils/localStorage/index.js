/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const storeStateLocally = (state, storageKey) => {
  const current = JSON.parse(
    localStorage.getItem(storageKey || 'sharedState') || '{}'
  );

  localStorage.setItem(
    storageKey || 'sharedState',
    JSON.stringify({...current, ...state})
  );
};

const clearStateLocally = (storageKey = 'sharedState') => {
  localStorage.removeItem(storageKey);
};

const getStateLocally = (storageKey) => {
  return JSON.parse(localStorage.getItem(storageKey || 'sharedState') || '{}');
};

export {storeStateLocally, clearStateLocally, getStateLocally};
