import {STATE} from 'modules/constants';

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
    activeIncident = incidents.filter(({state}) => state === STATE.ACTIVE)[0];
  }

  return activeIncident;
};

export function getInstanceState({state, incidents}) {
  if (
    state === STATE.COMPLETED ||
    state === STATE.CANCELED ||
    state === STATE.INCIDENT
  ) {
    return state;
  }

  // on Single instance view, instance.state is ACTIVE for active instance & instance with incident
  // so we look at instance.incidents
  // https://app.camunda.com/jira/browse/OPE-400
  const hasActiveIncident = Boolean(getActiveIncident(incidents));
  return hasActiveIncident ? STATE.INCIDENT : STATE.ACTIVE;
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

/**
 * @returns the instances with active operations from a given instances list
 * @param {Array} instances array of instance objects
 */
export function getInstancesWithActiveOperations(instances = []) {
  return instances.filter(instance => instance.hasActiveOperation);
}
