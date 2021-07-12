/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getTargetLineOptions} from './createTargetLineOptions';
import {extractCombinedData} from '../combinedChart/createCombinedChartData';
import {extractDefaultChartData} from '../defaultChart/createDefaultChartData';

export default function createTargetLineData(props) {
  const {combined} = props.report;

  if (combined) {
    return createCombinedTargetLineData(props);
  } else {
    return createSingleTargetLineData(props);
  }
}

function createSingleTargetLineData(props) {
  const {labels, formattedResult, targetValue, color, isDark} = extractDefaultChartData(props);

  const datasets = createSingleTargetLineDataset(
    targetValue,
    formattedResult,
    color,
    false,
    isDark
  );

  return {labels, datasets};
}

function createCombinedTargetLineData(props) {
  const {labels, unitedResults, reportsNames, reportColors, targetValue, isDark} =
    extractCombinedData(props);

  const datasets = unitedResults.reduce((prevDataset, reportData, i) => {
    return [
      ...prevDataset,
      ...createSingleTargetLineDataset(
        targetValue,
        reportData,
        reportColors[i],
        reportsNames[i],
        true,
        isDark
      ),
    ];
  }, []);

  return {labels, datasets};
}

function createSingleTargetLineDataset(targetValue, data, color, reportName, isCombined, isDark) {
  const allValues = data.map(({value}) => value);
  const {targetOptions, normalLineOptions} = getTargetLineOptions(
    color,
    targetValue.isBelow,
    isCombined,
    isDark
  );

  const datasets = [
    {
      data: allValues,
      ...targetOptions,
    },
    {
      label: reportName,
      data: allValues,
      ...normalLineOptions,
    },
  ];

  return datasets;
}
