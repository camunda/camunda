import {INSTANCE_STATE, ACTION_TYPE} from 'modules/constants';

export const getActiveIncident = (incidents = []) => {
  let activeIncident = null;

  if (incidents.length > 0) {
    activeIncident = incidents.filter(
      ({state}) => state === INSTANCE_STATE.ACTIVE
    )[0];
  }

  return activeIncident;
};

export function getInstanceState({state, incidents}) {
  if (state === INSTANCE_STATE.COMPLETED || state === INSTANCE_STATE.CANCELED) {
    return state;
  }

  const hasActiveIncident = Boolean(getActiveIncident(incidents));
  return hasActiveIncident ? INSTANCE_STATE.INCIDENT : INSTANCE_STATE.ACTIVE;
}

export function getIncidentMessage({incidents}) {
  return (getActiveIncident(incidents) || {}).errorMessage;
}

export function getWorkflowName({workflowId, workflowName}) {
  return workflowName || workflowId;
}
