/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createDatasetOptions} from '../defaultChart/createDefaultChartOptions';
import {uniteResults} from '../../service';
import {getCombinedChartProps} from './service';

export default function createCombinedChartData(props) {
  const {
    labels,
    unitedResults,
    reportsNames,
    reportColors,
    targetValue,
    isDark,
    visualization
  } = extractCombinedData(props);

  const datasets = unitedResults.map((report, index) => {
    return {
      label: reportsNames && reportsNames[index],
      data: report.map(({value}) => value),
      ...createDatasetOptions(visualization, report, targetValue, reportColors[index], true, isDark)
    };
  });

  return {labels, datasets};
}

export function extractCombinedData({report, theme, targetValue, flowNodeNames}) {
  const {result, data: combinedReportData} = report;

  const data = {...Object.values(result.data)[0].data, ...combinedReportData};

  const {reportsNames, resultArr, reportColors} = getCombinedChartProps(result.data, data);

  const isDark = theme === 'dark';

  let labels = [...new Set(resultArr.flat(2).map(({key}) => key))];

  const unitedResults = uniteResults(resultArr, labels);

  if (data.groupBy.type === 'flowNodes') {
    labels = labels.map(key => flowNodeNames[key] || key);
  }

  return {
    labels,
    unitedResults,
    reportsNames,
    reportColors,
    targetValue,
    isDark,
    visualization: data.visualization
  };
}
