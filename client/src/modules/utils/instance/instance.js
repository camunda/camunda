import {STATE} from 'modules/constants/instance';

export const getActiveIncident = (incidents = []) => {
  let activeIncident = null;

  if (incidents.length > 0) {
    activeIncident = incidents.filter(({state}) => state === STATE.ACTIVE)[0];
  }

  return activeIncident;
};

export function getInstanceState({state, incidents}) {
  if (state === STATE.COMPLETED || state === STATE.CANCELED) {
    return state;
  }

  const hasActiveIncident = Boolean(getActiveIncident(incidents));
  return hasActiveIncident ? STATE.INCIDENT : STATE.ACTIVE;
}

export function getIncidentMessage({incidents}) {
  return (getActiveIncident(incidents) || {}).errorMessage;
}

export function getWorkflowName({workflowId, workflowName}) {
  return workflowName || workflowId;
}
