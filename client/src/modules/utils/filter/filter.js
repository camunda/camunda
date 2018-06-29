export function getFilterQueryString(selection) {
  return `?filter=${JSON.stringify(selection)}`;
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
