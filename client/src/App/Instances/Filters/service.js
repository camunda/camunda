/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ALL_VERSIONS_OPTION} from './constants';
import {isValidDate} from 'modules/utils/date';
import {compactObject} from 'modules/utils';
import {trimValue} from 'modules/utils';
import {trimVariable} from 'modules/utils/variable';
import {sortBy} from 'lodash';

/**
 * Creates an array of {value: String, label: String} objects
 * used to create options list for workflowName select based on workflows data
 */
export const getOptionsForWorkflowName = (workflows = {}) => {
  let options = [];
  Object.keys(workflows).forEach((item) =>
    options.push({value: item, label: workflows[item].name || item})
  );

  // return case insensitive alphabetically sorted options by "label"
  return sortBy(options, (item) => item.label.toLowerCase());
};

/**
 * Creates an array of {value: String, label: String} objects
 * used to create options list for workflowIds select based on workflows list
 */
export function getOptionsForWorkflowVersion(versions = []) {
  return versions.map((item) => ({
    value: `${item.version}`,
    label: `Version ${item.version}`,
  }));
}

/**
 * Pushes an All version option to the given options array
 * used for workflowIds select
 */
export function addAllVersionsOption(options = []) {
  return options.length > 1
    ? [...options, {value: ALL_VERSIONS_OPTION, label: 'All versions'}]
    : [...options];
}

export function getLastVersionOfWorkflow(workflow = {}) {
  let version = '';
  if (workflow.workflows) {
    version = `${workflow.workflows[0].version}`;
  }

  return version;
}

export function isDateComplete(date) {
  const trimmedDate = trimValue(date);

  if (trimmedDate === '') {
    return true;
  }

  if (!isValidDate(trimmedDate)) {
    return false;
  }

  return !!trimmedDate.match(/^\d{4}-\d{2}-\d{2}(\W\d{2}:\d{2}(:\d{2})?)?$/);
}

export function isDateValid(date) {
  const trimmedDate = trimValue(date);

  if (trimmedDate === '') {
    return true;
  }

  return !!trimmedDate.match(/^[ \d:-]+$/);
}

export function isBatchOperationIdComplete(batchOperationId) {
  const trimmedBatchOperationId = trimValue(batchOperationId);

  if (trimmedBatchOperationId === '') {
    return true;
  }

  return /[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89aAbB][a-f0-9]{3}-[a-f0-9]{12}/.test(
    trimmedBatchOperationId
  );
}

export function isBatchOperationIdValid(batchOperationId) {
  const trimmedBatchOperationId = trimValue(batchOperationId);

  if (trimmedBatchOperationId === '') {
    return true;
  }

  return /^[a-f0-9-]{1,36}$/.test(trimmedBatchOperationId);
}

function isValidJson(value) {
  try {
    JSON.parse(value);
  } catch (e) {
    return false;
  }
  return true;
}

export function isVariableNameComplete(variable) {
  variable = trimVariable(variable);
  return !(variable.name === '' && variable.value !== '');
}

export function isVariableValueComplete(variable) {
  variable = trimVariable(variable);
  return !(variable.name !== '' && variable.value === '');
}

export function isVariableValueValid(variable) {
  variable = trimVariable(variable);
  return variable.value === '' || isValidJson(variable.value);
}

function isSingleIdValid(id) {
  return Boolean(id.match(/^\d{0,19}$/));
}

export function isIdValid(ids = '') {
  const hasInvalidCharacter = !Boolean(ids.match(/^[\d,\s]*$/));

  if (hasInvalidCharacter) {
    return false;
  }

  const hasInvalidIds = ids.split(/[,\s]/).some((id) => {
    return !isSingleIdValid(id.trim());
  });

  return !hasInvalidIds;
}

function isSingleIdComplete(id) {
  return id === '' || Boolean(id.match(/^\d{16,}$/));
}

export function isIdComplete(ids = '') {
  const hasIncompleteIds = ids.split(/[,\s]/).some((id) => {
    return !isSingleIdComplete(id.trim());
  });

  return isIdValid(ids) && !hasIncompleteIds;
}

function sanitizeVariable(variable) {
  if (!variable) return;
  if (
    variable.name !== '' &&
    isVariableNameComplete(variable) &&
    isVariableValueComplete(variable) &&
    isVariableValueValid(variable)
  ) {
    return variable;
  } else {
    return '';
  }
}

function sanitizeDate(date) {
  return isDateComplete(date) || date === '' ? date : '';
}

function sanitizeIds(ids = '') {
  return isIdComplete(ids) ? ids : '';
}

function sanitizeBatchOperationId(operationId = '') {
  return isBatchOperationIdComplete(operationId) ? operationId : '';
}

export function sanitizeFilter(filter) {
  const {variable, startDate, endDate, ids, batchOperationId} = filter;

  const sanatizeFcts = {
    ids: sanitizeIds,
    variable: sanitizeVariable,
    startDate: sanitizeDate,
    endDate: sanitizeDate,
    batchOperationId: sanitizeBatchOperationId,
  };

  // only add & sanatize filter when value available
  const addFilter = (type, value) => {
    return value ? {[type]: sanatizeFcts[type](value)} : null;
  };

  return compactObject({
    ...filter,
    ...addFilter('ids', ids),
    ...addFilter('variable', variable),
    ...addFilter('startDate', startDate),
    ...addFilter('endDate', endDate),
    ...addFilter('batchOperationId', batchOperationId),
  });
}

export const getFlowNodeOptions = (flowNodes) => {
  if (flowNodes.length < 1) {
    return [];
  }

  const options = flowNodes.map((flowNode) => ({
    value: flowNode.id,
    label: flowNode.name || flowNode.id,
  }));

  return sortBy(options, (flowNode) => flowNode.label.toLowerCase());
};
