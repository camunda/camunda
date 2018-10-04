import {isValidJSON} from 'modules/utils';
import {getSelectionById} from 'modules/utils/selection';
import {parseFilterForRequest} from 'modules/utils/filter';

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
  const {selection, selections, filter} = state;
  let selectiondata;

  if (selectionId) {
    selectiondata = getSelectionById(selections, selectionId);
  }

  return {
    // makes arrays out of 'ids' and 'excludeIds' Sets.
    queries: [
      {
        ...parseFilterForRequest(filter),
        ...selection,
        ids: [...(selection.ids || [])],
        excludeIds: [...(selection.excludeIds || [])]
      },
      ...(selectionId ? selections[selectiondata.index].queries : '')
    ]
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
