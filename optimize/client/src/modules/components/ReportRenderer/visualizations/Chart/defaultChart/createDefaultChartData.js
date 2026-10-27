/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatters, processResult} from 'services';

import {getAxisIdx, getLabel} from '../service';

import {createDatasetOptions} from './createDefaultChartOptions';

const {formatReportResult} = formatters;

export default function createDefaultChartData(props) {
  const {
    result: {measures = []},
    data: {
      configuration: {measureVisualizations, horizontalBar},
    },
  } = props.report;
  const chartData = measures.map((_, idx) => extractDefaultChartData(props, idx));
  const refLablesMap = chartData[0]?.labelsMap;
  const labels = chartData[0]?.labels;
  const datasets = chartData.map(
    ({formattedResult, visualization, targetValue, color, isDark}, idx) => {
      const measure = measures[idx];
      let type = visualization;
      let order;

      if (visualization === 'barLine') {
        type = measureVisualizations[measure.property];
        order = type === 'line' ? 0 : 1;
      }

      return {
        [horizontalBar ? 'xAxisID' : 'yAxisID']: 'axis-' + getAxisIdx(measures, idx),
        label: getLabel(measure),
        data: getValues(refLablesMap, formattedResult),
        formatter: formatters[measure.property],
        order,
        ...createDatasetOptions({
          type,
          data: formattedResult,
          targetValue,
          datasetColor: color,
          isStriped: false,
          isDark,
          measureCount: measures.length,
          datasetIdx: idx,
        }),
      };
    }
  );

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

  const labelsMap = formattedResult.map(({key, label}) => ({
    key,
    label: formatters.formatLabel(label || key),
  }));
  const labels = labelsMap.map(({label}) => label);

  return {
    labels,
    labelsMap,
    formattedResult,
    visualization,
    targetValue,
    color,
    isDark,
  };
}

// get values based on key
function getValues(refLabelsMap, formattedResult) {
  return refLabelsMap.map(({key}) => formattedResult.find((result) => result.key === key).value);
}
