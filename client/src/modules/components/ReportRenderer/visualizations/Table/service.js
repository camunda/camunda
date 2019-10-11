/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'request';

import {reportConfig, formatters} from 'services';
import {getRelativeValue} from '../service';
import {t} from 'translation';

const {
  options: {view, groupBy},
  getLabelFor
} = reportConfig.process;

const {formatReportResult} = formatters;

export async function getCamundaEndpoints() {
  const response = await get('api/camunda');
  return await response.json();
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
          ...(displayRelativeValue ? [t('report.table.relativeFrequency')] : [])
        ]
      }
    ],
    []
  );
}

export function getBodyRows(
  unitedResults,
  allKeys,
  formatter,
  displayRelativeValue,
  instanceCount,
  displayAbsoluteValue,
  flowNodeNames = {}
) {
  const rows = allKeys.map((key, idx) => {
    const row = [flowNodeNames[key] || key];
    unitedResults.forEach((result, i) => {
      const value = result[idx].value;
      if (displayAbsoluteValue) {
        row.push(formatter(typeof value !== 'undefined' && value !== null ? value : ''));
      }
      if (displayRelativeValue) {
        row.push(getRelativeValue(value, instanceCount[i]));
      }
    });
    return row;
  });
  return rows;
}

export function getCombinedTableProps(reportResult, reports) {
  const initialData = {
    labels: [],
    reportsNames: [],
    combinedResult: [],
    instanceCount: []
  };

  const combinedProps = reports.reduce((prevReport, {id}) => {
    const report = reportResult[id];
    const {data, result, name} = report;

    // build 2d array of all labels
    const viewLabel = getLabelFor('view', view, data.view);
    const groupByLabel = getLabelFor('groupBy', groupBy, data.groupBy);
    const labels = [...prevReport.labels, [groupByLabel, viewLabel]];

    // 2d array of all names
    const reportsNames = [...prevReport.reportsNames, name];

    // 2d array of all results
    const formattedResult = formatReportResult(data, result.data);
    const reportsResult = [...prevReport.combinedResult, formattedResult];

    // 2d array of all process instances count
    const reportsInstanceCount = [...prevReport.instanceCount, result.instanceCount];

    return {
      labels,
      reportsNames,
      combinedResult: reportsResult,
      instanceCount: reportsInstanceCount
    };
  }, initialData);

  return combinedProps;
}
