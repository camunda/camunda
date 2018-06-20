/**
 * extracts only necessary statistics from instance statistics response
 */
export function extractInstanceStats({
  workflowId,
  startDate,
  endDate,
  state,
  incidents
}) {
  let instanceStats = {
    workflowId,
    startDate,
    endDate,
    stateName: state
  };

  if (state === 'COMPLETED' || state === 'CANCELLED') {
    return instanceStats;
  }

  // get the active incident
  const activeIncident =
    incidents &&
    incidents.length &&
    incidents.filter(({state}) => state === 'ACTIVE')[0];

  if (!activeIncident) {
    return instanceStats;
  }

  instanceStats = {
    ...instanceStats,
    stateName: 'INCIDENT',
    errorMessage: activeIncident.errorMessage
  };

  return instanceStats;
}
