/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {reportConfig, formatters, isDurationReport} from 'services';
import {getRelativeValue} from '../service';

const {formatReportResult} = formatters;

export default function processDefaultData({formatter = v => v, report, flowNodeNames = {}}) {
  const {data, result, reportType} = report;
  const {
    configuration: {hideAbsoluteValue, hideRelativeValue, xml},
    view,
    groupBy
  } = data;

  const formattedResult = formatReportResult(data, result.data);
  const instanceCount = result.processInstanceCount || result.decisionInstanceCount || 0;
  const config = reportConfig[reportType];
  const labels = [
    config.getLabelFor(config.options.groupBy, groupBy, xml),
    config.getLabelFor(config.options.view, view, xml)
  ];

  if (view.entity === 'userTask') {
    labels[0] = 'User Task';
  }

  const displayRelativeValue = view.property === 'frequency' && !hideRelativeValue;
  const displayAbsoluteValue = isDurationReport(report) || !hideAbsoluteValue;

  if (!displayAbsoluteValue) {
    labels.length = 1;
  }

  // normal two-dimensional data
  return {
    head: [...labels, ...(displayRelativeValue ? ['Relative Frequency'] : [])],
    body: formattedResult.map(({key, value}) => [
      flowNodeNames[key] || key,
      ...(displayAbsoluteValue ? [formatter(value)] : []),
      ...(displayRelativeValue ? [getRelativeValue(value, instanceCount)] : [])
    ])
  };
}
