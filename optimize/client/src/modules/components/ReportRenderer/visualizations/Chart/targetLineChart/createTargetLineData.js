/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ColorPicker} from 'components';

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

  const datasets = createSingleTargetLineDataset({
    targetValue,
    data: formattedResult,
    color,
    isCombined: false,
    isDark,
  });

  return {labels, datasets};
}

function createCombinedTargetLineData(props) {
  const {
    report: {result},
  } = props;

  const {labels, unitedResults, reportsNames, reportColors, targetValue, isDark} =
    extractCombinedData(props);

  let colors = reportColors;
  if (result.measures) {
    colors = ColorPicker.getGeneratedColors(result.measures[0].data[0].value.length);
  }

  const datasets = unitedResults.reduce((prevDataset, reportData, i) => {
    return [
      ...prevDataset,
      ...createSingleTargetLineDataset({
        targetValue,
        data: reportData,
        color: colors[i],
        reportName: reportsNames[i],
        isCombined: true,
        isDark,
      }),
    ];
  }, []);

  return {labels, datasets};
}

function createSingleTargetLineDataset({targetValue, data, color, reportName, isCombined, isDark}) {
  const allValues = data.map(({value}) => value);
  const {targetOptions, normalLineOptions} = getTargetLineOptions(
    color,
    targetValue.isBelow,
    isCombined,
    isDark
  );

  const datasets = [
    {
      yAxisID: 'axis-0',
      data: allValues,
      ...targetOptions,
    },
    {
      yAxisID: 'axis-0',
      label: reportName,
      data: allValues,
      ...normalLineOptions,
    },
  ];

  return datasets;
}
