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
    (view.operation === 'avg' ||
      view.operation === 'min' ||
      view.operation === 'max' ||
      view.operation === 'median') &&
    view.property === 'duration' &&
    groupBy.type === 'flowNodes' &&
    visualization === 'heat'
  );
}

export function isSingleNumber({processDefinitionKey, processDefinitionVersion, visualization}) {
  return processDefinitionKey && processDefinitionVersion && visualization === 'number';
}
