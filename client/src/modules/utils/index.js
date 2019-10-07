/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE} from 'modules/constants';

export function isValidJSON(text) {
  try {
    JSON.parse(text);
    return true;
  } catch (e) {
    return false;
  }
}

/**
 * @returns a boolean showing if the current instance is running.
 * @param {*} instance object with full instance data
 */
export const isRunning = instance => {
  return instance.state === STATE.ACTIVE || instance.state === STATE.INCIDENT;
};

/**
 * Similar to lodash's compact(array): Removes entries with falsy values from the object
 * i.e. false, null, 0, "", undefined, and NaN
 * @param {object} object: object to make compact
 */
export function compactObject(object) {
  return Object.entries(object).reduce((obj, [key, value]) => {
    return !!value ? {...obj, [key]: value} : obj;
  }, {});
}

/**
 * @returns a filtered object containing only entries of the provided keys
 * @param {*} object
 * @param any[] keys
 */
export function pickFromObject(object, keys) {
  return Object.entries(object).reduce((result, [key, value]) => {
    return !keys.includes(key) ? result : {...result, [key]: value};
  }, {});
}

/**
 * immutable version of array[index] = updatedValue
 * @returns the original array with the provided updatedValue at the provided index
 * @param {any[]} array
 * @param {number} index
 * @param {any} updatedValue
 */
export function immutableArraySet(array, index, updatedValue) {
  return [...array.slice(0, index), updatedValue, ...array.slice(index + 1)];
}

export function trimValue(value) {
  return typeof value === 'string' ? value.trim() : value;
}
