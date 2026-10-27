/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatters} from 'services';
import {ColorPicker} from 'components';

import {createDatasetOptions} from '../defaultChart/createDefaultChartOptions';
import {getAxisIdx, getLabel} from '../service';
import {getHyperChartProps} from './service';

export default function createHyperChartData(props) {
  const {
    report: {result},
  } = props;

  if (result.measures) {
    return createMultiMeasureChartData(props);
  }

  const {labels, unitedResults, reportsNames, reportColors, targetValue, isDark, visualization} =
    extractHyperData(props);

  const datasets = unitedResults.map((report, index) => {
    return {
      yAxisID: 'axis-0',
      label: reportsNames && reportsNames[index],
      data: report.map(({value}) => value),
      ...createDatasetOptions({
        type: visualization,
        data: report,
        targetValue,
        datasetColor: reportColors[index],
        isStriped: true,
        isDark,
      }),
    };
  });

  return {labels, datasets};
}

function createMultiMeasureChartData(props) {
  const {
    report: {
      result: {measures},
      data: {
        configuration: {measureVisualizations, stackedBar, horizontalBar},
      },
    },
  } = props;

  let labels = [];
  const datasets = [];
  const colors = ColorPicker.getGeneratedColors(measures.length * measures[0].data[0].value.length);

  measures.forEach((measure, idx) => {
    const {
      labels: measureLabels,
      unitedResults,
      reportsNames,
      targetValue,
      isDark,
      visualization,
    } = extractHyperData(props, idx);

    let type = visualization;
    let order;
    if (visualization === 'barLine') {
      type = measureVisualizations[measure.property];
      order = type === 'line' ? 0 : 1;
    }

    unitedResults.forEach((report, index) => {
      datasets.push({
        [horizontalBar ? 'xAxisID' : 'yAxisID']: 'axis-' + getAxisIdx(measures, idx),
        label:
          reportsNames &&
          reportsNames[index] + (measures.length > 1 ? ' - ' + getLabel(measure) : ''),
        data: report.map(({value}) => value),
        formatter: formatters[measure.property],
        order,
        stack: stackedBar ? idx : undefined,
        ...createDatasetOptions({
          type,
          data: report,
          targetValue,
          datasetColor: colors[idx * unitedResults.length + index],
          isStriped: true,
          isDark,
          stackedBar,
        }),
      });
    });

    labels = measureLabels;
  });

  return {labels, datasets};
}

export function extractHyperData({report, theme, targetValue}, measureIdx = 0) {
  const {result, data: hyperReportData} = report;

  const data = {...Object.values(result.data)[0].data, ...hyperReportData};

  const {reportsNames, resultArr, reportColors} = getHyperChartProps(result.data, data, measureIdx);

  const isDark = theme === 'dark';
  const labelsMap = {};

  const keys = Array.from(
    new Set(
      resultArr.flat(2).map(({key, label}) => {
        labelsMap[key] = label;
        return key;
      })
    )
  );

  if (data.groupBy.type.includes('Date')) {
    keys.sort((a, b) => new Date(a) - new Date(b));
  }

  const unitedResults = uniteResults(resultArr, keys);
  let labels =
    data.visualization === 'number' ? reportsNames : keys.map((key) => labelsMap[key] || key);
  labels = labels.map((label) => formatters.formatLabel(label));

  return {
    labels,
    unitedResults,
    reportsNames,
    reportColors,
    targetValue,
    isDark,
    visualization: data.visualization,
  };
}

function uniteResults(results, allKeys) {
  const unitedResults = [];
  results.forEach((result) => {
    const resultObj = formatters.objectifyResult(result);
    const newResult = [];
    allKeys.forEach((key) => {
      if (typeof resultObj[key] === 'undefined') {
        newResult.push({key, value: null});
      } else {
        newResult.push({key, value: resultObj[key]});
      }
    });
    unitedResults.push(newResult);
  });

  return unitedResults;
}
