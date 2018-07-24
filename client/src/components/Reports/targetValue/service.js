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
    view &&
    groupBy &&
    visualization &&
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

export function isBarChart({processDefinitionKey, processDefinitionVersion, visualization}) {
  return processDefinitionKey && processDefinitionVersion && visualization === 'bar';
}

export function isValidNumber(value) {
  if (typeof value === 'number') {
    return true;
  }
  if (typeof value === 'string') {
    return value.trim() && !isNaN(value.trim()) && +value >= 0;
  }
}
