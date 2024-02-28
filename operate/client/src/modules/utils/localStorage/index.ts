/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const DEFAULT_STORAGE_KEY = 'sharedState';

function storeStateLocally(state: any, storageKey = DEFAULT_STORAGE_KEY) {
  const current = JSON.parse(localStorage.getItem(storageKey) || '{}');

  localStorage.setItem(storageKey, JSON.stringify({...current, ...state}));
}

function clearStateLocally(storageKey = DEFAULT_STORAGE_KEY) {
  localStorage.removeItem(storageKey);
}

function getStateLocally(storageKey = DEFAULT_STORAGE_KEY) {
  return JSON.parse(localStorage.getItem(storageKey) || '{}');
}

export {storeStateLocally, clearStateLocally, getStateLocally};
