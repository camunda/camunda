import {get} from 'request';

import {reportConfig, formatters} from 'services';

const {view, groupBy, getLabelFor} = reportConfig;

const {formatReportResult} = formatters;

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
      const value = result[key];
      if (displayAbsoluteValue) row.push(formatter(typeof value !== 'undefined' ? value : ''));
      if (displayRelativeValue) row.push(getRelativeValue(value, processInstanceCount[i]));
    });
    return row;
  });
  return rows;
}

export function getCombinedTableProps(reportResult, reportIds) {
  const initialData = {
    labels: [],
    reportsNames: [],
    combinedResult: [],
    processInstanceCount: []
  };

  const combinedProps = reportIds.reduce((prevReport, reportId) => {
    const report = reportResult[reportId];
    const {data, result, processInstanceCount, name} = report;

    // build 2d array of all labels
    const viewLabel = getLabelFor(view, data.view);
    const groupByLabel = getLabelFor(groupBy, data.groupBy);
    const labels = [...prevReport.labels, [groupByLabel, viewLabel]];

    // 2d array of all names
    const reportsNames = [...prevReport.reportsNames, name];

    // 2d array of all results
    const formattedResult = formatReportResult(data, result);
    const reportsResult = [...prevReport.combinedResult, formattedResult];

    // 2d array of all process instances count
    const reportsProcessInstanceCount = [...prevReport.processInstanceCount, processInstanceCount];

    return {
      labels,
      reportsNames,
      combinedResult: reportsResult,
      processInstanceCount: reportsProcessInstanceCount
    };
  }, initialData);

  return combinedProps;
}
