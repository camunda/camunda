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

export function parseQueryString(queryString) {
  var params = {};

  const queries = queryString
    .replace(/%22/g, '"')
    .substring(1)
    .split('&');

  queries.forEach((item, index) => {
    const temp = queries[index].split('=');

    if (isValidJSON(temp[1])) {
      params[temp[0]] = JSON.parse(temp[1]);
    }
  });

  return params;
}

export function createNewSelectionFragment() {
  return {query: {ids: new Set()}, exclusionList: new Set()};
}
