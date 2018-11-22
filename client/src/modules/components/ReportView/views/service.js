import {get} from 'request';

export async function getCamundaEndpoints() {
  const response = await get('api/camunda');
  return await response.json();
}

export function getRelativeValue(data, total) {
  if (data === null) return '';
  return Math.round(data / total * 1000) / 10 + '%';
}

export function getFormattedLabels(
  reportsLabels,
  reportsNames,
  displayRelativeValue,
  displayAbsoluteValue
) {
  return reportsLabels.reduce(
    (prev, reportLabels, i) => [
      ...prev,
      {
        label: reportsNames[i],
        columns: [
          ...(displayAbsoluteValue ? reportLabels.slice(1) : []),
          ...(displayRelativeValue ? ['Relative Frequency'] : [])
        ]
      }
    ],
    []
  );
}

export function uniteResults(results, allKeys) {
  const unitedResults = [];
  results.forEach(result => {
    const newResult = {};
    allKeys.forEach(key => {
      if (typeof result[key] === 'undefined') {
        newResult[key] = null;
      } else {
        newResult[key] = result[key];
      }
    });
    unitedResults.push(newResult);
  });
  return unitedResults;
}

export function getBodyRows(
  unitedResults,
  allKeys,
  formatter,
  displayRelativeValue,
  processInstanceCount,
  displayAbsoluteValue
) {
  const rows = allKeys.map(key => {
    const row = [key];
    unitedResults.forEach((result, i) => {
      if (displayAbsoluteValue) row.push(formatter(result[key] || ''));
      if (displayRelativeValue) row.push(getRelativeValue(result[key], processInstanceCount[i]));
    });
    return row;
  });
  return rows;
}

export function calculateLinePosition(chart) {
  const yAxis = chart.scales[chart.options.scales.yAxes[0].id];

  return (1 - chart.options.lineAt / yAxis.max) * yAxis.height + yAxis.top;
}
