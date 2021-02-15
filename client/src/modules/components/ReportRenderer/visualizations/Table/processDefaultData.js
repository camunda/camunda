/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {reportConfig, formatters, isDurationReport} from 'services';
import {t} from 'translation';

const {formatReportResult, getRelativeValue, duration} = formatters;

export default function processDefaultData({formatter = (v) => v, report}) {
  const {data, result, reportType} = report;
  const {
    configuration: {hideAbsoluteValue, hideRelativeValue},
    view,
    groupBy,
  } = data;

  const formattedResult = formatReportResult(data, result.data);
  const instanceCount = result.instanceCount || 0;
  const config = reportConfig[reportType];
  const labels = [
    config.getLabelFor('groupBy', config.options.groupBy, groupBy),
    config.getLabelFor('view', config.options.view, view),
  ];

  if (view.entity === 'userTask' && groupBy.type === 'userTasks') {
    labels[0] = t('report.view.userTask');
  }

  const displayRelativeValue = view.properties[0] === 'frequency' && !hideRelativeValue;
  const displayAbsoluteValue = isDurationReport(report) || !hideAbsoluteValue;

  if (!displayAbsoluteValue) {
    labels.length = 1;
  }

  const groupedByDuration = groupBy.type === 'duration';

  // normal two-dimensional data
  return {
    head: [...labels, ...(displayRelativeValue ? [t('report.table.relativeFrequency')] : [])],
    body: formattedResult.map(({label, key, value}) => [
      groupedByDuration ? duration(label) : label || key,
      ...(displayAbsoluteValue ? [formatter(value)] : []),
      ...(displayRelativeValue ? [getRelativeValue(value, instanceCount)] : []),
    ]),
  };
}
