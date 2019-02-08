export function isDurationHeatmap(reportResult) {
  const {
    processDefinitionKey,
    processDefinitionVersion,
    view,
    groupBy,
    visualization
  } = reportResult.data;
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
