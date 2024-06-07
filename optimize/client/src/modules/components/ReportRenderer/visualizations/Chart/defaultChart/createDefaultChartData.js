/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {formatters, processResult} from 'services';

import {getAxisIdx, getLabel} from '../service';
import {createDatasetOptions} from './createDefaultChartOptions';

const {formatReportResult} = formatters;

export default function createDefaultChartData(props) {
  const datasets = [];

  const {
    result: {measures = []},
    data: {
      configuration: {measureVisualizations, horizontalBar},
    },
  } = props.report;

  const chartData = measures.map((_, idx) => extractDefaultChartData(props, idx));
  const labels = chartData[0].labels;

  chartData.forEach(
    ({labels: measureLabels, formattedResult, visualization, targetValue, color, isDark}, idx) => {
      const measure = measures[idx];
      let type = visualization;
      let order;
      if (visualization === 'barLine') {
        type = measureVisualizations[measure.property];
        order = type === 'line' ? 0 : 1;
      }

      datasets.push({
        [horizontalBar ? 'xAxisID' : 'yAxisID']: 'axis-' + getAxisIdx(measures, idx),
        label: getLabel(measure),
        data: sortValues(
          labels,
          measureLabels,
          formattedResult.map(({value}) => value)
        ),
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
      });
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

  const labels = formattedResult.map(({key, label}) => formatters.formatLabel(label || key));

  return {
    labels,
    formattedResult,
    visualization,
    targetValue,
    color,
    isDark,
  };
}

// sorts values based on refLabels
function sortValues(refLabels, labels, values) {
  let labelValueMap = {};
  labels.forEach((label, index) => {
    labelValueMap[label] = values[index];
  });

  return refLabels.map((label) => labelValueMap[label]);
}
