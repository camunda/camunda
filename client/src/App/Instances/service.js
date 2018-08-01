import {isValidJSON} from 'modules/utils';

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

export function getParentFilter(filter) {
  const appliedFilters = Object.keys(filter);
  if (appliedFilters.includes('incidents' || 'active')) return {running: true};
  if (appliedFilters.includes('completed' || 'canceled'))
    return {finished: true};
}
