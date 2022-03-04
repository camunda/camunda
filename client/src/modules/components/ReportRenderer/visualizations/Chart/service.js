/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getTooltipText} from 'services';
import {t} from 'translation';

import {getColorFor} from './colorsUtils';

export function formatTooltip({
  dataset,
  dataIndex,
  configuration: {alwaysShowAbsolute, alwaysShowRelative},
  formatter,
  instanceCount,
  isDuration,
  showLabel,
}) {
  if (dataset.isTarget) {
    return;
  }

  const tooltipText = getTooltipText(
    dataset.data[dataIndex],
    formatter,
    instanceCount,
    alwaysShowAbsolute,
    alwaysShowRelative,
    isDuration
  );

  if (!tooltipText) {
    return;
  }

  const label = showLabel && dataset.label ? dataset.label + ': ' : '';

  return label + tooltipText;
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

export function drawHorizentalLine(chart) {
  if (chart.options.lineAt >= 0 && chart.options.lineAt !== false) {
    const ctx = chart.ctx;
    const xAxe = chart.scales['xAxes'];
    const lineAt = calculateLinePosition(chart);

    ctx.save();
    ctx.strokeStyle = getColorFor('targetBar', true);
    ctx.setLineDash([10, 10]);
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(xAxe.left, lineAt);
    ctx.lineTo(xAxe.right, lineAt);
    ctx.stroke();
    ctx.restore();
  }
}

export function calculateLinePosition(chart) {
  const yAxis = chart.scales['axis-0'];

  return (1 - chart.options.lineAt / yAxis.max) * yAxis.height + yAxis.top;
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
