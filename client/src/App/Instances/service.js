export function parseFilterForRequest(filter) {
  const {active, incidents} = filter;
  let payload = {running: active || incidents};

  payload.withoutIncidents = active || false;
  payload.withIncidents = incidents || false;

  return payload;
}
