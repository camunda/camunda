/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ALL_VERSIONS_OPTION} from './constants';
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
  Object.keys(workflows).forEach(item =>
    options.push({value: item, label: workflows[item].name || item})
  );

  // return case insensitive alphabetically sorted options by "label"
  return sortBy(options, item => item.label.toLowerCase());
};

/**
 * Creates an array of {value: String, label: String} objects
 * used to create options list for workflowIds select based on workflows list
 */
export function getOptionsForWorkflowVersion(versions = []) {
  return versions.map(item => ({
    value: `${item.version}`,
    label: `Version ${item.version}`
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

export function checkIsDateComplete(date) {
  const trimmedDate = trimValue(date);

  if (trimmedDate === '') {
    return true;
  }

  const parsedDate = new Date(trimmedDate);
  if (!(parsedDate instanceof Date) || isNaN(parsedDate)) {
    return false;
  }

  return !!trimmedDate.match(/^\d{4}-\d{2}-\d{2}(\W\d{2}:\d{2}(:\d{2})?)?$/);
}

function isValidJson(value) {
  try {
    JSON.parse(value);
  } catch (e) {
    return false;
  }
  return true;
}

export function checkIsVariableNameComplete(variable) {
  variable = trimVariable(variable);
  return !(variable.name === '' && variable.value !== '');
}

export function checkIsVariableValueComplete(variable) {
  variable = trimVariable(variable);
  return !(variable.name !== '' && variable.value === '');
}

export function checkIsVariableValueValid(variable) {
  variable = trimVariable(variable);
  return variable.value === '' || isValidJson(variable.value);
}

function checkIsSingleIdValid(id) {
  return Boolean(id.match(/^\d{0,19}$/));
}

export function checkIsIdValid(ids = '') {
  const hasInvalidCharacter = !Boolean(ids.match(/^[\d,\s]*$/));

  if (hasInvalidCharacter) {
    return false;
  }

  const hasInvalidIds = ids.split(/[,\s]/).some(id => {
    return !checkIsSingleIdValid(id.trim());
  });

  return !hasInvalidIds;
}

function checkIsSingleIdComplete(id) {
  return id === '' || Boolean(id.match(/^\d{16,}$/));
}

export function checkIsIdComplete(ids = '') {
  const hasIncompleteIds = ids.split(/[,\s]/).some(id => {
    return !checkIsSingleIdComplete(id.trim());
  });

  return checkIsIdValid(ids) && !hasIncompleteIds;
}

function sanitizeVariable(variable) {
  if (!variable) return;
  if (
    variable.name !== '' &&
    checkIsVariableNameComplete(variable) &&
    checkIsVariableValueComplete(variable) &&
    checkIsVariableValueValid(variable)
  ) {
    return variable;
  } else {
    return '';
  }
}

function sanitizeDate(date) {
  return checkIsDateComplete(date) || date === '' ? date : '';
}

function sanitizeIds(ids = '') {
  return checkIsIdComplete(ids) ? ids : '';
}

export function sanitizeFilter(filter) {
  const {variable, startDate, endDate, ids} = filter;

  return compactObject({
    ...filter,
    ids: sanitizeIds(ids),
    variable: sanitizeVariable(variable),
    startDate: sanitizeDate(startDate),
    endDate: sanitizeDate(endDate)
  });
}

const sortByFlowNodeLabel = flowNodes => {
  return sortBy(flowNodes, flowNode => flowNode.label.toLowerCase());
};

const addValueAndLabel = bpmnElement => {
  return {
    ...bpmnElement,
    value: bpmnElement.id,
    label: bpmnElement.name
      ? bpmnElement.name
      : 'Unnamed' + bpmnElement.$type.split(':')[1].replace(/([A-Z])/g, ' $1')
  };
};

/** Needs comment  + tests */

export const sortAndModify = bpmnElements => {
  if (bpmnElements.length < 1) {
    return [];
  }

  const named = [];
  const unnamed = [];

  bpmnElements.forEach(bpmnElement => {
    const enhancedElement = addValueAndLabel(bpmnElement);

    if (enhancedElement.name) {
      named.push(enhancedElement);
    } else {
      unnamed.push(enhancedElement);
    }
  });

  return [...sortByFlowNodeLabel(named), ...sortByFlowNodeLabel(unnamed)];
};
