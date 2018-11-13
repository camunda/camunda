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

export function isChart(reportResult) {
  const {reportType} = reportResult;
  let {
    visualization,
    reportIds,
    processDefinitionKey,
    processDefinitionVersion
  } = reportResult.data;

  if (reportType === 'combined') {
    if (!reportIds || !reportIds.length) return false;
    visualization = reportResult.result[reportIds[0]].data.visualization;
  }

  return (
    ((processDefinitionKey && processDefinitionVersion) || reportIds) &&
    (visualization === 'bar' || visualization === 'line')
  );
}

export function isValidNumber(value) {
  if (typeof value === 'number') {
    return true;
  }
  if (typeof value === 'string') {
    return value.trim() && !isNaN(value.trim()) && +value >= 0;
  }
}
