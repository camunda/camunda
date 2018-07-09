export function getFilterQueryString(selection) {
  const filtered = Object.keys(selection)
    .filter(key => selection[key] === true)
    .reduce((obj, key) => {
      obj[key] = selection[key];
      return obj;
    }, {});

  return `?filter=${JSON.stringify(filtered)}`;
}

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
}
