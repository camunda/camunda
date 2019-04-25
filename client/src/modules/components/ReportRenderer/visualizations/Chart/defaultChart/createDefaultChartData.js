/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createDatasetOptions} from './createDefaultChartOptions';
import {formatters} from 'services';

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
      data: formattedResult.map(({value}) => value),
      ...createDatasetOptions(visualization, formattedResult, targetValue, color, false, isDark)
    }
  ];

  return {labels, datasets};
}

export function extractDefaultChartData({report, theme, targetValue}) {
  const {result, data} = report;
  const isDark = theme === 'dark';
  const {
    visualization,
    configuration: {color}
  } = data;

  const formattedResult = formatReportResult(data, result.data);
  formattedResult.reverse();

  let labels = formattedResult.map(({key, label}) => label || key);

  return {
    labels,
    formattedResult,
    visualization,
    targetValue,
    color,
    isDark
  };
}
