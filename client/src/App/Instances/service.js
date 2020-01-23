/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isEmpty, isEqual} from 'lodash';

import {parseDiagramXML} from 'modules/utils/bpmn';

import {getWorkflowByVersion} from 'modules/utils/filter';

export function decodeFields(object) {
  let result = {};

  for (let key in object) {
    const value = object[key];
    result[key] = typeof value === 'string' ? decodeURI(object[key]) : value;
  }
  return result;
}

export function getWorkflowName(workflow) {
  return workflow ? workflow.name || workflow.bpmnProcessId : 'Workflow';
}

export async function fetchDiagramModel(dataManager, workflowId) {
  const xml = await dataManager.getWorkflowXML(workflowId);
  return await parseDiagramXML(xml);
}

export function getWorkflowByVersionFromFilter({
  filter: {workflow, version},
  groupedWorkflows
}) {
  return getWorkflowByVersion(groupedWorkflows[workflow], version);
}

export function getWorkflowNameFromFilter({filter, groupedWorkflows}) {
  const currentWorkflowByVersion = getWorkflowByVersionFromFilter({
    filter,
    groupedWorkflows
  });

  if (!isEmpty(currentWorkflowByVersion)) {
    return getWorkflowName(currentWorkflowByVersion);
  }

  const currentWorkflow = groupedWorkflows[filter.workflow];
  return getWorkflowName(currentWorkflow);
}

export function hasWorkflowChanged(prevFilter, filter) {
  return (
    prevFilter.workflow !== filter.workflow ||
    prevFilter.version !== filter.version
  );
}

export function hasFirstElementChanged(prevElement, element) {
  return prevElement !== element;
}

export function hasSortingChanged(prevSorting, sorting) {
  return !isEqual(prevSorting, sorting);
}

export function hasUrlChanged(prevLocation, location) {
  return prevLocation.search !== location.search;
}
export function hasFilterChanged(prevFilter, filter) {
  return !isEqual(prevFilter, filter);
}
