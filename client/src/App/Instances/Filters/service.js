export function parseWorkflowNames(workflows) {
  return workflows.map(item => ({
    value: item.bpmnProcessId,
    label: item.name || item.bpmnProcessId
  }));
}

export const fieldParser = {
  errorMessage: value => (value.length === 0 ? null : value),
  ids: value => value.split(/[ ,]+/).filter(Boolean)
};
