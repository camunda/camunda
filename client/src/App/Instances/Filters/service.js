export const FIELDS = {
  errorMessage: {name: 'errorMessage', placeholder: 'Error Message'},
  ids: {name: 'ids', placeholder: 'Instance Id(s) separated by space or comma'},
  workflowName: {name: 'workflowName', placeholder: 'Workflow'},
  workflowVersion: {name: 'workflowVersion', placeholder: 'Workflow Version'},
  flowNode: {name: 'flowNode', placeholder: 'Flow Node'},
  startDate: {name: 'startDate', placeholder: 'Start Date'},
  endDate: {name: 'endDate', placeholder: 'End Date'}
};

export function parseWorkflowNames(workflows) {
  return workflows.map(item => ({
    value: item.bpmnProcessId,
    label: item.name || item.bpmnProcessId
  }));
}

export function parseWorkflowVersions(versions = []) {
  return versions.map(item => ({
    value: item.id,
    label: `Version ${item.version}`
  }));
}

export function addAllVersionsOption(options = []) {
  options.push({value: 'all', label: 'All versions'});
  return options;
}

const parseDate = value => value;

export const fieldParser = {
  errorMessage: value => (value.length === 0 ? null : value),
  ids: value => value.split(/[ ,]+/).filter(Boolean),
  startDate: parseDate,
  endDate: parseDate
};
