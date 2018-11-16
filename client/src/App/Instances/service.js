import {isValidJSON} from 'modules/utils';
import {getSelectionById} from 'modules/utils/selection';
import {
  parseFilterForRequest,
  getFilterWithWorkflowIds
} from 'modules/utils/filter';

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

export function getPayload({selectionId, state}) {
  const {selection, selections, filter, groupedWorkflowInstances} = state;
  const filterWithWorkflowIds = getFilterWithWorkflowIds(
    filter,
    groupedWorkflowInstances
  );
  let selectionIndex;

  if (selectionId) {
    selectionIndex = getSelectionById(selections, selectionId).index;
  }

  const query = {
    ...parseFilterForRequest(filterWithWorkflowIds)
  };

  if (!selection.all) {
    query.ids = [...selection.ids];
  } else {
    query.excludeIds = [...selection.excludeIds];
  }

  return {
    queries: [query, ...(selectionId ? selections[selectionIndex].queries : '')]
  };
}

export function getPayloadtoFetchInstancesById(IdsOfInstancesInSelections) {
  let query = {
    ids: [...IdsOfInstancesInSelections],
    running: true,
    active: true,
    canceled: true,
    completed: true,
    finished: true,
    incidents: true
  };

  return {queries: [query]};
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

export function createMapOfInstances(workflowInstances) {
  const transformedInstances = workflowInstances.reduce((acc, instance) => {
    return {
      [instance.id]: instance,
      ...acc
    };
  }, {});
  return new Map(Object.entries(transformedInstances));
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

export function getActivityIds(nodes) {
  let activityIds = [];

  for (let node in nodes) {
    if (nodes[node].$type === 'bpmn:ServiceTask') {
      activityIds.push({
        value: nodes[node].id,
        label: nodes[node].name || 'Unnamed task'
      });
    }
  }

  return activityIds;
}
