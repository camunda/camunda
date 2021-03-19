/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ColorPicker} from 'components';
import {formatters, getReportResult} from 'services';

import CombinedReportRenderer from './CombinedReportRenderer';

const {formatReportResult} = formatters;

export default function HyperReportRenderer({report, ...rest}) {
  const convertedReport = {
    ...report,
  };

  const result = getReportResult(report);

  const firstEntryResult = result.data[0].value.filter(isVisible(report));

  const colors = ColorPicker.getGeneratedColors(firstEntryResult.length);

  convertedReport.combined = true;
  convertedReport.data = {
    configuration: report.data.configuration,
    reports: firstEntryResult.map(({key}, i) => ({id: key, color: colors[i]})),
    visualization: getVisualization(report.data.visualization),
  };

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
        data: result.data.map((entry) => ({
          ...entry,
          value: entry.value.find((data) => data.key === key).value,
        })),
      },
    };
    delete newResultData[key].result.measures;
  });

  convertedReport.result = {
    ...result,
    type: 'hyperMap',
    data: newResultData,
  };

  return <CombinedReportRenderer {...rest} report={convertedReport} />;
}

function getVisualization(visualization) {
  if (['table', 'line'].includes(visualization)) {
    return visualization;
  }
  return 'bar';
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

function isVisible(report) {
  const {hiddenNodes} = report.data.configuration;

  return ({key}) => {
    if (
      ['flowNode', 'userTask'].includes(report.data.distributedBy.type) &&
      hiddenNodes.active &&
      hiddenNodes.keys.includes(key)
    ) {
      return false;
    }

    return true;
  };
}
