import {INSTANCE_STATE} from 'modules/constants';

/**
 * @returns the last operation from an operations list or an empty {}
 * @param {*} operations array of operations
 */
export const getLatestOperation = (operations = []) => {
  return operations.length > 0 ? operations[0] : {};
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
  if (
    state === INSTANCE_STATE.COMPLETED ||
    state === INSTANCE_STATE.CANCELED ||
    state === INSTANCE_STATE.INCIDENT
  ) {
    return state;
  }

  // on Single instance view, instance.state is ACTIVE for active instance & instance with incident
  // so we look at instance.incidents t
  const hasActiveIncident = Boolean(getActiveIncident(incidents));
  return hasActiveIncident ? INSTANCE_STATE.INCIDENT : INSTANCE_STATE.ACTIVE;
}

export function getIncidentMessage({incidents}) {
  return (getActiveIncident(incidents) || {}).errorMessage;
}

export function getWorkflowName({bpmnProcessId, workflowName}) {
  return workflowName || bpmnProcessId;
}

export function formatGroupedWorkflows(workflows = []) {
  return workflows.reduce((obj, value) => {
    obj[value.bpmnProcessId] = {
      ...value
    };

    return obj;
  }, {});
}
