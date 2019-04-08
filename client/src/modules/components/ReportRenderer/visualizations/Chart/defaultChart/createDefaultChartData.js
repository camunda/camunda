/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createDatasetOptions} from './createDefaultChartOptions';
import {formatters} from 'services';
import {isDate} from '../service';

const {formatReportResult} = formatters;

export default function createDefaultChartData(props) {
  const {
    labels,
    formattedResult,
    visualization,
    targetValue,
    color,
    isDark
  } = extractDefaultChartData(props);

  const datasets = [
    {
      data: Object.values(formattedResult),
      ...createDatasetOptions(visualization, formattedResult, targetValue, color, false, isDark)
    }
  ];

  return {labels, datasets};
}

export function extractDefaultChartData({report, theme, targetValue, flowNodeNames}) {
  const {result, data} = report;
  const isDark = theme === 'dark';
  const {
    groupBy,
    visualization,
    configuration: {color}
  } = data;
  const formattedResult = formatReportResult(data, result.data);

  let labels = Object.keys(formattedResult);

  if (isDate(groupBy)) {
    labels.sort((a, b) => {
      return new Date(a) - new Date(b);
    });
  }

  if (data.groupBy.type === 'flowNodes') {
    labels = labels.map(key => flowNodeNames[key] || key);
  }

  return {
    labels,
    formattedResult,
    visualization,
    targetValue,
    color,
    isDark
  };
}
