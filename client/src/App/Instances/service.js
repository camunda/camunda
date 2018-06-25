export function parseFilterForRequest(filter) {
  const {active, incidents} = filter;
  let payload = {running: true};

  if (active && incidents) {
    return payload;
  }

  if (!active && !incidents) {
    payload.running = false;

    return payload;
  }

  if (active || incidents) {
    if (active) {
      payload.withoutIncidents = true;
    }

    if (incidents) {
      payload.withIncidents = true;
    }
  }

  return payload;
}
