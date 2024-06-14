/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import update from 'immutability-helper';

import {formatters, getReportResult} from 'services';

import CombinedReportRenderer from './CombinedReportRenderer';
import ProcessReportRenderer from './ProcessReportRenderer';

const {formatReportResult} = formatters;

export default function HyperReportRenderer({report, ...rest}) {
  const result = getReportResult(report);

  if (!result.data.length || result.instanceCount === 0) {
    const data = result.data.map((entry) => ({...entry, value: 0}));
    const emptyReport = update(report, {result: {measures: {$set: [{data}]}}});
    return <ProcessReportRenderer {...rest} report={emptyReport} />;
  }

  const firstEntryResult = result.data[0].value;
  const newResultData = {};

  formatResult(report.data, firstEntryResult).forEach(({key, label}) => {
    newResultData[key] = {
      combined: false,
      id: key,
      name: label,
      reportType: 'process',
      data: report.data,
      result: {
        ...result,
        type: 'map',
        measures: result.measures.map((measure) => ({
          ...measure,
          type: 'map',
          data: measure.data.map((datapoint) => ({
            ...datapoint,
            value: datapoint.value.find((data) => data.key === key)?.value,
          })),
        })),
      },
    };
  });

  const convertedReport = {
    ...report,
    combined: true,
    data: {
      configuration: report.data.configuration,
      reports: firstEntryResult.map(({key}) => ({id: key})),
      visualization: report.data.visualization,
    },
    result: {
      ...result,
      type: 'hyperMap',
      data: newResultData,
    },
  };

  return <CombinedReportRenderer {...rest} report={convertedReport} />;
}

function formatResult(data, result) {
  const {
    distributedBy,
    configuration: {distributeByDateVariableUnit},
  } = data;

  const distributedByDateVar =
    distributedBy.type === 'variable' && distributedBy.value.type === 'Date';
  const distributedByDate = ['startDate', 'endDate'].includes(distributedBy.type);

  if (distributedByDate || distributedByDateVar) {
    return formatReportResult(
      {
        ...data,
        groupBy: distributedBy,
        configuration: {
          ...data.configuration,
          groupByDateVariableUnit: distributeByDateVariableUnit,
        },
      },
      result
    );
  }

  return result;
}
