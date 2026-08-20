/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatters} from 'services';

import {
  sortColumns,
  getFormattedLabels,
  getBodyRows,
  getHyperTableProps,
  formatLabelsForTableBody,
} from './service';

export default function processHyperData({report}) {
  const {
    configuration: {hideAbsoluteValue, hideRelativeValue, tableColumns, precision},
  } = report.data;
  const {view, groupBy} = Object.values(report.result.data)[0].data;

  const displayRelativeValue = view.properties[0] === 'frequency' && !hideRelativeValue;
  const displayAbsoluteValue = !hideAbsoluteValue;

  const {labels, reportsNames, reportsIds, combinedResult, instanceCount} = getHyperTableProps(
    report.result.data,
    report.data.reports,
    displayRelativeValue,
    displayAbsoluteValue
  );

  const keysLabel = labels[0][0];

  const formattedLabels = getFormattedLabels(labels, reportsNames, reportsIds);

  const flowNodeNames = {};
  // get all unique keys of results of multiple reports and build flowNodesNames hash
  const allKeys = Array.from(
    new Set(
      combinedResult
        .flat() // flatten measures
        .map(({data}) => data) // extract data from measures object
        .flat() // flatten all datapoints
        .map(({key, label}) => {
          // extract flownode name labels and keys
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
    displayRelativeValue,
    instanceCount,
    displayAbsoluteValue,
    flowNodeNames,
    groupedByDuration: groupBy.type === 'duration',
    precision,
  });

  let head = [{id: keysLabel, label: ' ', columns: [keysLabel]}, ...formattedLabels];
  let body = rows;
  if (tableColumns) {
    const {sortedHead, sortedBody} = sortColumns(head, rows, tableColumns.columnOrder);
    head = sortedHead;
    body = sortedBody;
  }

  body = formatLabelsForTableBody(body);

  return {
    head,
    body,
  };
}

function uniteResults(results, allKeys) {
  const unitedResults = [];
  results.forEach((measures) => {
    unitedResults.push(
      measures.map((measure) => {
        const resultObj = formatters.objectifyResult(measure.data);
        const newResult = [];
        allKeys.forEach((key) => {
          if (typeof resultObj[key] === 'undefined') {
            newResult.push({key, value: null});
          } else {
            newResult.push({key, value: resultObj[key]});
          }
        });
        return {...measure, data: newResult};
      })
    );
  });

  return unitedResults;
}
