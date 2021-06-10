/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {formatters, processResult} from 'services';

import {getAxisIdx, getLabel} from '../service';
import {createDatasetOptions} from './createDefaultChartOptions';

const {formatReportResult} = formatters;

export default function createDefaultChartData(props) {
  const datasets = [];
  let labels = [];

  const {result} = props.report;
  const measures = result.measures;

  measures.forEach((measure, idx) => {
    const {
      labels: measureLabels,
      formattedResult,
      visualization,
      targetValue,
      color,
      isDark,
    } = extractDefaultChartData(props, idx);

    datasets.push({
      yAxisID: 'axis-' + getAxisIdx(measures, idx),
      label: getLabel(measure),
      data: formattedResult.map(({value}) => value),
      formatter: formatters[measure.property],
      ...createDatasetOptions({
        type: visualization,
        data: formattedResult,
        targetValue,
        datasetColor: color,
        isStriped: false,
        isDark,
        measureCount: measures.length,
        datasetIdx: idx,
      }),
    });

    labels = measureLabels;
  });

  return {labels, datasets};
}

export function extractDefaultChartData({report, theme, targetValue}, measureIdx = 0) {
  const {data} = report;
  const isDark = theme === 'dark';
  const {
    visualization,
    configuration: {color},
  } = data;

  const result = processResult({...report, result: report.result.measures[measureIdx]});
  const formattedResult = formatReportResult(data, result.data);

  const labels = formattedResult.map(({key, label}) => label || key);

  return {
    labels,
    formattedResult,
    visualization,
    targetValue,
    color,
    isDark,
  };
}
