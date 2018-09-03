import {isValidJSON} from 'modules/utils';
import {getSelectionById} from 'modules/utils/selection';

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

export function createNewSelectionFragment() {
  return {ids: new Set(), excludeIds: new Set()};
}

export function getParentFilter(appliedFilters) {
  return 'incidents' in appliedFilters || 'active' in appliedFilters
    ? {running: true}
    : {finished: true};
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
        ...filter,
        ...getParentFilter(filter),
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
