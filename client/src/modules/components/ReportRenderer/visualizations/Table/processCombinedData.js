/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {sortColumns, getFormattedLabels, getBodyRows, getCombinedTableProps} from './service';
import {uniteResults} from '../service';

export default function processCombinedData({formatter, report}) {
  const {labels, reportsNames, reportsIds, combinedResult, instanceCount} = getCombinedTableProps(
    report.result.data,
    report.data.reports
  );

  const {
    configuration: {hideAbsoluteValue, hideRelativeValue, tableColumns},
  } = report.data;
  const {view, groupBy} = Object.values(report.result.data)[0].data;

  const displayRelativeValue = view.properties[0] === 'frequency' && !hideRelativeValue;
  const displayAbsoluteValue = !hideAbsoluteValue;

  const keysLabel = labels[0][0];

  const formattedLabels = getFormattedLabels(
    labels,
    reportsNames,
    reportsIds,
    displayRelativeValue,
    displayAbsoluteValue
  );

  const flowNodeNames = {};
  // get all unique keys of results of multiple reports and build flowNodesNames hash
  const allKeys = Array.from(
    new Set(
      combinedResult.flat(2).map(({key, label}) => {
        flowNodeNames[key] = label;
        return key;
      })
    )
  );

  // make all hash tables look exactly the same by filling empty keys with empty string
  const unitedResults = uniteResults(combinedResult, allKeys);

  // convert hashtables into a table rows array
  const rows = getBodyRows({
    unitedResults,
    allKeys,
    formatter,
    displayRelativeValue,
    instanceCount,
    displayAbsoluteValue,
    flowNodeNames,
    groupedByDuration: groupBy.type === 'duration',
  });

  const head = [{id: keysLabel, label: ' ', columns: [keysLabel]}, ...formattedLabels];

  if (tableColumns) {
    const {sortedHead, sortedBody} = sortColumns(head, rows, tableColumns.columnOrder);
    return {
      head: sortedHead,
      body: sortedBody,
    };
  }

  return {
    head,
    body: rows,
  };
}
