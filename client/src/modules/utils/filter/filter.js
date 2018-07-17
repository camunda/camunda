export function getFilterQueryString(selection) {
  const filtered = Object.entries(selection).reduce((obj, [key, value]) => {
    return !!value ? {...obj, [key]: value} : obj;
  }, {});

  return `?filter=${JSON.stringify(filtered)}`;
}

export function parseFilterForRequest(filter) {
  const {active, incidents, completed, canceled} = filter;
  let payload = {
    running: active || incidents,
    finished: completed || canceled
  };

  return {
    ...payload,
    ...filter
  };
}
