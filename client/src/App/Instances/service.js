import {isEmpty} from 'lodash';

import {isValidJSON} from 'modules/utils';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {getWorkflowByVersion} from 'modules/utils/filter';

export function parseQueryString(queryString = '') {
  var params = {};

  const queries = queryString
    .replace(/%22/g, '"')
    .substring(1)
    .split('&');

  queries.forEach((item, index) => {
    const [paramKey, paramValue] = queries[index].split('=');

    if (isValidJSON(paramValue)) {
      params[paramKey] = JSON.parse(paramValue);
    }
  });

  return params;
}

export function getStateUpdateForAddSelection(
  selection,
  rollingSelectionIndex,
  instancesInSelectionsCount,
  selectionCount,
  prevState
) {
  const currentSelectionIndex = rollingSelectionIndex + 1;
  const newCount = instancesInSelectionsCount + selection.totalCount;
  return {
    selections: [
      {
        selectionId: currentSelectionIndex,
        ...selection
      },
      ...prevState.selections
    ],
    rollingSelectionIndex: currentSelectionIndex,
    instancesInSelectionsCount: newCount,
    selectionCount: selectionCount + 1,
    openSelection: currentSelectionIndex,
    selection: {all: false, ids: [], excludeIds: []}
  };
}

export function decodeFields(object) {
  let result = {};

  for (let key in object) {
    const value = object[key];
    result[key] = typeof value === 'string' ? decodeURI(object[key]) : value;
  }
  return result;
}

export function getEmptyDiagramMessage(name) {
  return `There is more than one version selected for Workflow "${name}".\n
   To see a diagram, select a single version.`;
}

export function getTaskNodes(bpmnElements) {
  const nodes = formatDiagramNodes(bpmnElements);
  let taskNodes = [];

  for (let node in nodes) {
    if (nodes[node].type === 'bpmn:ServiceTask') {
      taskNodes.push({
        id: nodes[node].id,
        name: nodes[node].name || 'Unnamed task'
      });
    }
  }

  return taskNodes;
}

export function getWorkflowName(workflow) {
  return workflow ? workflow.name || workflow.bpmnProcessId : 'Workflow';
}

export function formatDiagramNodes(nodes = {}) {
  const result = [];
  for (let node in nodes) {
    const item = nodes[node];
    result.push({type: item['$type'], id: item.id, name: item.name});
  }
  return result;
}

export async function fetchDiagramModel(workflowId) {
  const xml = await fetchWorkflowXML(workflowId);
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
