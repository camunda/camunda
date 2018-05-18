export function isDurationHeatmap({
  processDefinitionKey,
  processDefinitionVersion,
  view,
  groupBy,
  visualization
}) {
  return (
    processDefinitionKey &&
    processDefinitionVersion &&
    view.entity === 'flowNode' &&
    view.operation === 'avg' &&
    view.property === 'duration' &&
    groupBy.type === 'flowNode' &&
    visualization === 'heat'
  );
}

export function isSingleNumber({processDefinitionKey, processDefinitionVersion, visualization}) {
  return processDefinitionKey && processDefinitionVersion && visualization === 'number';
}
