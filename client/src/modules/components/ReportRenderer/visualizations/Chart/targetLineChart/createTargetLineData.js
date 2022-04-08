/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
