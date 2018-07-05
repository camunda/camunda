function isValidJSON(text) {
  try {
    JSON.parse(text);
    return true;
  } catch (e) {
    return false;
  }
}

export function isEmpty(obj) {
  for (var key in obj) {
    if (obj.hasOwnProperty(key)) return false;
  }
  return true;
}

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
  return {query: {ids: new Set()}, exclusionList: new Set()};
}
