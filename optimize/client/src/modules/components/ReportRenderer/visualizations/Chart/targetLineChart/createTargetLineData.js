/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ColorPicker} from 'components';

import {getTargetLineOptions} from './createTargetLineOptions';
import {extractHyperData} from '../hyperChart/createHyperChartData';
import {extractDefaultChartData} from '../defaultChart/createDefaultChartData';

export default function createTargetLineData(props) {
  const {hyper} = props.report;

  if (hyper) {
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
    isHyper: false,
    isDark,
  });

  return {labels, datasets};
}

function createCombinedTargetLineData(props) {
  const {
    report: {result},
  } = props;

  const {labels, unitedResults, reportsNames, reportColors, targetValue, isDark} =
    extractHyperData(props);

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
        isHyper: true,
        isDark,
      }),
    ];
  }, []);

  return {labels, datasets};
}

function createSingleTargetLineDataset({targetValue, data, color, reportName, isHyper, isDark}) {
  const allValues = data.map(({value}) => value);
  const {targetOptions, normalLineOptions} = getTargetLineOptions(
    color,
    targetValue.isBelow,
    isHyper,
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
