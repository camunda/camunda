/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {formatters} from 'services';

import {sortColumns, getFormattedLabels, getBodyRows, getCombinedTableProps} from './service';

export default function processCombinedData({report}) {
  const {
    configuration: {hideAbsoluteValue, hideRelativeValue, tableColumns},
  } = report.data;
  const {view, groupBy} = Object.values(report.result.data)[0].data;

  const displayRelativeValue = view.properties[0] === 'frequency' && !hideRelativeValue;
  const displayAbsoluteValue = !hideAbsoluteValue;

  const {labels, reportsNames, reportsIds, combinedResult, instanceCount} = getCombinedTableProps(
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
