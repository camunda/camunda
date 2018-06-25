export function parseFilterForRequest(filter) {
  const {active, incidents} = filter;
  let payload = {running: active || incidents};

  if (!active || !incidents) {
    payload.withoutIncidents = active;
    payload.withIncidents = incidents;
  }

  return payload;
}
