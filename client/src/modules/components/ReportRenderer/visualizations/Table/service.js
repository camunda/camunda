/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {reportConfig, formatters} from 'services';
import {t} from 'translation';

const {
  options: {view, groupBy},
  getLabelFor,
} = reportConfig.process;

const {formatReportResult, getRelativeValue, duration} = formatters;

export function getFormattedLabels(
  reportsLabels,
  reportsNames,
  reportsIds,
  displayRelativeValue,
  displayAbsoluteValue
) {
  return reportsLabels.reduce(
    (prev, reportLabels, i) => [
      ...prev,
      {
        label: reportsNames[i],
        id: reportsIds[i],
        columns: [
          ...(displayAbsoluteValue ? reportLabels.slice(1) : []),
          ...(displayRelativeValue ? [t('report.table.relativeFrequency')] : []),
        ],
      },
    ],
    []
  );
}

export function getBodyRows({
  unitedResults,
  allKeys,
  formatter,
  displayRelativeValue,
  instanceCount,
  displayAbsoluteValue,
  flowNodeNames = {},
  groupedByDuration,
}) {
  const rows = allKeys.map((key, idx) => {
    const row = [groupedByDuration ? duration(key) : flowNodeNames[key] || key];
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
    reportsIds: [],
    combinedResult: [],
    instanceCount: [],
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

    // 2d array of all ids
    const reportsIds = [...prevReport.reportsIds, id];

    // 2d array of all results
    const formattedResult = formatReportResult(data, result.data);
    const reportsResult = [...prevReport.combinedResult, formattedResult];

    // 2d array of all process instances count
    const reportsInstanceCount = [...prevReport.instanceCount, result.instanceCount];

    return {
      labels,
      reportsNames,
      reportsIds,
      combinedResult: reportsResult,
      instanceCount: reportsInstanceCount,
    };
  }, initialData);

  return combinedProps;
}

export function sortColumns(head, body, columnOrder) {
  if (!columnOrder.length) {
    return {sortedHead: head, sortedBody: body};
  }

  const sortedHead = head.slice().sort(byOrder(columnOrder));

  const sortedBody = body.map((row) => row.map(valueForNewColumnPosition(head, sortedHead)));

  return {sortedHead, sortedBody};
}

function byOrder(order) {
  return function (a, b) {
    let indexA = order.indexOf(a.id || a);
    let indexB = order.indexOf(b.id || b);

    // put columns without specified order at end
    if (indexA === -1) {
      indexA = Infinity;
    }
    if (indexB === -1) {
      indexB = Infinity;
    }

    return indexA - indexB;
  };
}

function valueForNewColumnPosition(head, sortedHead) {
  const flattendHead = flatten(head);
  const flattendSortedHead = flatten(sortedHead);

  return function (_, newPosition, cells) {
    const headerAtNewPosition = flattendSortedHead[newPosition];
    const originalPosition = flattendHead.indexOf(headerAtNewPosition);

    return cells[originalPosition];
  };
}

function flatten(head) {
  const flattendHead = head.reduce((arr, el) => {
    let headColumns = [el];
    if (el.columns) {
      headColumns = el.columns.map((col) => el.id + col);
    }

    return arr.concat(headColumns);
  }, []);

  return flattendHead;
}
