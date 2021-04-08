/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createDatasetOptions} from './createDefaultChartOptions';
import {formatters, processResult} from 'services';
import {t} from 'translation';

const {formatReportResult} = formatters;

export default function createDefaultChartData(props) {
  const datasets = [];
  let labels = [];

  const {result} = props.report;

  const measures = result.measures;

  function getAxisIdx({property}) {
    if (measures.every(({property}) => property === measures[0].property)) {
      // if every measure has the same prop, there is only one axis
      return 0;
    }
    return property === 'frequency' ? 0 : 1;
  }

  function getLabel({property, aggregationType, userTaskDurationTime}) {
    return (
      (userTaskDurationTime
        ? `${t('report.config.userTaskDuration.' + userTaskDurationTime)} `
        : '') +
      t('report.view.' + (property === 'frequency' ? 'count' : 'duration')) +
      (aggregationType ? ` - ${t('report.config.aggregationShort.' + aggregationType)}` : '')
    );
  }

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
      yAxisID: 'axis-' + getAxisIdx(measure),
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
