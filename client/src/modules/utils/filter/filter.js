export function getFilterQueryString(selection) {
  return `?filter=${JSON.stringify(selection)}`;
}

export function parseFilterForRequest(filter) {
  const {active, incidents} = filter;
  let payload = {running: active || incidents};

  return {
    ...payload,
    active,
    incidents
  };
}
