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

export const fieldParser = {
  errorMessage: value => (value.length === 0 ? null : value),
  ids: value => value.split(/[ ,]+/).filter(Boolean)
};
