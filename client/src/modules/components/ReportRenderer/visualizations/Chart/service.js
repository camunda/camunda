/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getTooltipText, isDurationReport} from 'services';
import {t} from 'translation';

export function formatTooltip({
  dataset,
  dataIndex,
  configuration: {alwaysShowAbsolute, alwaysShowRelative, precision},
  formatter,
  instanceCount,
  isDuration,
  showLabel,
}) {
  if (dataset.isTarget) {
    return;
  }

  const shortNotation = isDuration ? alwaysShowAbsolute : alwaysShowAbsolute || alwaysShowRelative;

  const tooltipText = getTooltipText(
    dataset.data[dataIndex],
    formatter,
    instanceCount,
    alwaysShowAbsolute,
    alwaysShowRelative,
    isDuration,
    precision,
    shortNotation
  );

  if (!tooltipText) {
    return;
  }

  const label = showLabel && dataset.label ? dataset.label + ': ' : '';

  return shortNotation ? tooltipText : label + tooltipText;
}

export function formatTooltipTitle(title, availableWidth) {
  if (!title || !availableWidth) {
    return '';
  }

  const widthPerCharacter = 7;
  const charactersPerLine = Math.floor(availableWidth / widthPerCharacter);

  const lines = [];
  let remainingString = title;

  while (remainingString.length > charactersPerLine) {
    const currentString = remainingString.substr(0, charactersPerLine);
    let lastSpaceIndex = currentString.lastIndexOf(' ');
    const hasSuitableWhitespace = lastSpaceIndex !== -1;

    if (!hasSuitableWhitespace) {
      lastSpaceIndex = charactersPerLine;
    }

    lines.push(currentString.substr(0, lastSpaceIndex));
    remainingString = remainingString.substr(lastSpaceIndex + hasSuitableWhitespace);
  }
  lines.push(remainingString);

  return lines.join('\n');
}

export function getTooltipLabelColor({dataIndex, dataset}, type) {
  if (type === 'pie') {
    const color = dataset.backgroundColor[dataIndex];
    return {
      borderColor: color,
      backgroundColor: color,
    };
  }

  return {
    borderColor: dataset.legendColor,
    backgroundColor: dataset.legendColor,
  };
}

export function calculateLinePosition(chart) {
  const firstMeasureAxis = chart.scales['axis-0'];

  return firstMeasureAxis.getPixelForValue(chart.options.lineAt);
}

export function canBeInterpolated({type, value}, xml, decisionDefinitionKey) {
  if (type === 'flowNodes' || type === 'userTasks') {
    return false;
  }
  if (type === 'variable' && value.type === 'String') {
    return false;
  }
  if (type === 'inputVariable' || type === 'outputVariable') {
    return (
      new DOMParser()
        .parseFromString(xml, 'text/xml')
        .querySelector(
          `decision[id="${decisionDefinitionKey}"] [id="${value.id}"] ${
            type === 'inputVariable' ? 'inputExpression' : ''
          }`
        )
        .getAttribute('typeRef')
        .toLowerCase() !== 'string'
    );
  }
  return true;
}

export function getLabel({property, aggregationType, userTaskDurationTime}) {
  return (
    (userTaskDurationTime
      ? `${t('report.config.userTaskDuration.' + userTaskDurationTime)} `
      : '') +
    t('report.view.' + (property === 'frequency' ? 'count' : 'duration')) +
    (aggregationType
      ? ` - ${t('report.config.aggregationShort.' + aggregationType.type, {
          value: aggregationType.value,
        })}`
      : '')
  );
}

export function getAxisIdx(measures, measureIdx) {
  if (measures.every(({property}) => property === measures[0].property)) {
    // if every measure has the same prop, there is only one axis
    return 0;
  }
  return measures[measureIdx].property === 'frequency' ? 0 : 1;
}

export function hasReportPersistedTooltips(report) {
  const {
    data: {
      configuration: {alwaysShowAbsolute, alwaysShowRelative},
    },
  } = report;
  const isDuration = isDurationReport(report);
  return isDuration ? alwaysShowAbsolute : alwaysShowAbsolute || alwaysShowRelative;
}

export function getAxesConfig(isHorizontal) {
  return {
    axis0: {
      position: isHorizontal ? 'bottom' : 'left',
      axis: isHorizontal ? 'x' : 'y',
      id: 'axis-0',
    },
    axis1: {position: isHorizontal ? 'top' : 'right', axis: isHorizontal ? 'x' : 'y', id: 'axis-1'},
    groupBy: {
      position: isHorizontal ? 'left' : 'bottom',
      axis: isHorizontal ? 'y' : 'x',
      id: isHorizontal ? 'y' : 'x',
    },
  };
}
