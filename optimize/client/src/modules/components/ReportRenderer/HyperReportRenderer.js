/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import update from 'immutability-helper';

import {formatters, getReportResult, processResult as processSingleReportResult} from 'services';

import {Table, Chart} from './visualizations';
import ProcessReportRenderer from './ProcessReportRenderer';
import {getFormatter} from './service';

const {formatReportResult} = formatters;

const getComponent = (visualization) => {
  if (visualization === 'table') {
    return Table;
  } else {
    return Chart;
  }
};

function processResult(reports) {
  return Object.entries(reports).reduce((result, [reportId, report]) => {
    result[reportId] = {...report, result: processSingleReportResult(report)};
    return result;
  }, {});
}

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
      id: key,
      name: label,
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
    hyper: true,
    data: {
      configuration: report.data.configuration,
      reports: firstEntryResult.map(({key}) => ({id: key})),
      visualization: report.data.visualization,
    },
    result: {
      ...result,
      type: 'hyperMap',
      data: processResult(newResultData),
    },
  };

  const {view, visualization} = Object.values(convertedReport.result.data)[0].data;
  const Component = getComponent(visualization);

  return (
    <div className="component">
      <Component {...rest} report={convertedReport} formatter={getFormatter(view.properties[0])} />
    </div>
  );
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
