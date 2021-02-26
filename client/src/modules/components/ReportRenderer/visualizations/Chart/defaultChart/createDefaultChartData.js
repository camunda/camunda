/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createDatasetOptions} from './createDefaultChartOptions';
import {reportConfig, formatters, processResult} from 'services';
import {t} from 'translation';

const {formatReportResult} = formatters;

export default function createDefaultChartData(props) {
  const datasets = [];
  let labels = [];

  const {data, result, reportType} = props.report;

  const measures = result.measures;
  const config = reportConfig[reportType];

  const selectedView = config.findSelectedOption(config.options.view, 'data', data.view);
  const viewString = t('report.view.' + selectedView.key.split('_')[0]);

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
      yAxisID: 'axis-' + idx,
      label: `${viewString} ${t(
        'report.view.' + (measure.property === 'frequency' ? 'count' : 'duration')
      )}`,
      data: formattedResult.map(({value}) => value),
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
