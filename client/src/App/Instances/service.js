<<<<<<< HEAD
function isValidJSON(text) {
  try {
    JSON.parse(text);
    return true;
  } catch (e) {
    return false;
  }
=======
export const defaultFilterSelection = {
  active: true,
  incidents: true
};

export const incidentsFilterSelection = {
  ...defaultFilterSelection,
  active: false
};

export function parseFilterForRequest(filter) {
  const {active, incidents, completed, canceled: cancelled} = filter;
  let payload = {
    running: active || incidents,
    finished: completed || cancelled
  };

  return {
    ...payload,
    active,
    incidents,
    completed,
    cancelled
  };
>>>>>>> feat(Filter): add completed instances api calls
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
