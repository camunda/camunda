import {orderBy} from 'lodash';

import {INSTANCE_STATE} from 'modules/constants';

/**
 * @returns an array of operations sorted in ascending order by startDate
 * @param {*} operations array of operations
 */
export const getLatestOperation = (operations = []) => {
  return operations.length > 0
    ? orderBy(operations, ['startDate'], ['desc'])[0]
    : '';
};

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

export function getWorkflowName({bpmnProcessId, workflowName}) {
  return workflowName || bpmnProcessId;
}
