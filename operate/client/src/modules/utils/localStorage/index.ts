/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
