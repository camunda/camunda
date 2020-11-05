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
  let options: any = [];
  Object.keys(workflows).forEach((item) =>
    // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
    options.push({value: item, label: workflows[item].name || item})
  );

  // return case insensitive alphabetically sorted options by "label"
  return sortBy(options, (item: any) => item.label.toLowerCase());
};

/**
 * Creates an array of {value: String, label: String} objects
 * used to create options list for workflowIds select based on workflows list
 */
export function getOptionsForWorkflowVersion(versions = []) {
  return versions.map((item) => ({
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'version' does not exist on type 'never'.
    value: `${item.version}`,
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'version' does not exist on type 'never'.
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
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'workflows' does not exist on type '{}'.
  if (workflow.workflows) {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'workflows' does not exist on type '{}'.
    version = `${workflow.workflows[0].version}`;
  }

  return version;
}

export function isDateComplete(date: any) {
  const trimmedDate = trimValue(date);

  if (trimmedDate === '') {
    return true;
  }

  if (!isValidDate(trimmedDate)) {
    return false;
  }

  return !!trimmedDate.match(/^\d{4}-\d{2}-\d{2}(\W\d{2}:\d{2}(:\d{2})?)?$/);
}

export function isDateValid(date: any) {
  const trimmedDate = trimValue(date);

  if (trimmedDate === '') {
    return true;
  }

  return !!trimmedDate.match(/^[ \d:-]+$/);
}

export function isBatchOperationIdComplete(batchOperationId: any) {
  const trimmedBatchOperationId = trimValue(batchOperationId);

  if (trimmedBatchOperationId === '') {
    return true;
  }

  return /[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89aAbB][a-f0-9]{3}-[a-f0-9]{12}/.test(
    trimmedBatchOperationId
  );
}

export function isBatchOperationIdValid(batchOperationId: any) {
  const trimmedBatchOperationId = trimValue(batchOperationId);

  if (trimmedBatchOperationId === '') {
    return true;
  }

  return /^[a-f0-9-]{1,36}$/.test(trimmedBatchOperationId);
}

function isValidJson(value: any) {
  try {
    JSON.parse(value);
  } catch (e) {
    return false;
  }
  return true;
}

export function isVariableNameComplete(variable: any) {
  variable = trimVariable(variable);
  return !(variable.name === '' && variable.value !== '');
}

export function isVariableValueComplete(variable: any) {
  variable = trimVariable(variable);
  return !(variable.name !== '' && variable.value === '');
}

export function isVariableValueValid(variable: any) {
  variable = trimVariable(variable);
  return variable.value === '' || isValidJson(variable.value);
}

function isSingleIdValid(id: any) {
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

function isSingleIdComplete(id: any) {
  return id === '' || Boolean(id.match(/^\d{16,}$/));
}

export function isIdComplete(ids = '') {
  const hasIncompleteIds = ids.split(/[,\s]/).some((id) => {
    return !isSingleIdComplete(id.trim());
  });

  return isIdValid(ids) && !hasIncompleteIds;
}

function sanitizeVariable(variable: any) {
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

function sanitizeDate(date: any) {
  return isDateComplete(date) || date === '' ? date : '';
}

function sanitizeIds(ids = '') {
  return isIdComplete(ids) ? ids : '';
}

function sanitizeBatchOperationId(operationId = '') {
  return isBatchOperationIdComplete(operationId) ? operationId : '';
}

export function sanitizeFilter(filter: any) {
  const {variable, startDate, endDate, ids, batchOperationId} = filter;

  const sanatizeFcts = {
    ids: sanitizeIds,
    variable: sanitizeVariable,
    startDate: sanitizeDate,
    endDate: sanitizeDate,
    batchOperationId: sanitizeBatchOperationId,
  };

  // only add & sanatize filter when value available
  const addFilter = (type: any, value: any) => {
    // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
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

export const getFlowNodeOptions = (flowNodes: any) => {
  if (flowNodes.length < 1) {
    return [];
  }

  const options = flowNodes.map((flowNode: any) => ({
    value: flowNode.id,
    label: flowNode.name || flowNode.id,
  }));

  return sortBy(options, (flowNode: any) => flowNode.label.toLowerCase());
};
