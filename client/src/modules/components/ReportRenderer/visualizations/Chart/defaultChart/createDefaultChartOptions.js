/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getFormattedTargetValue} from './service';
import {formatTooltip, getTooltipLabelColor, canBeInterpolated} from '../service';
import {isDurationReport, formatters} from 'services';
import {getColorFor, createColors, determineBarColor} from '../colorsUtils';

const {createDurationFormattingOptions, duration} = formatters;

export default function createDefaultChartOptions({report, targetValue, theme, formatter}) {
  const {
    data: {visualization, groupBy, configuration, decisionDefinitionKey},
    result,
  } = report;

  const isDark = theme === 'dark';
  const isDuration = isDurationReport(report);
  const maxValue = isDuration ? Math.max(...result.data.map(({value}) => value)) : 0;
  const isPersistedTooltips = isDuration
    ? configuration.alwaysShowAbsolute
    : configuration.alwaysShowAbsolute || configuration.alwaysShowRelative;

  const groupedByDurationMaxValue =
    groupBy?.type === 'duration' && Math.max(...result.data.map(({label}) => +label));

  let options;
  switch (visualization) {
    case 'pie':
      options = createPieOptions(isDark);
      break;
    case 'line':
    case 'bar':
      options = createBarOptions({
        targetValue,
        configuration,
        stacked: false,
        maxDuration: maxValue,
        groupedByDurationMaxValue,
        isDark,
        isPersistedTooltips,
        autoSkip: canBeInterpolated(groupBy, configuration.xml, decisionDefinitionKey),
      });
      break;
    default:
      options = {};
  }

  const tooltipCallbacks = {
    label: (tooltipItem, data) => {
      return formatTooltip(
        tooltipItem,
        data,
        configuration,
        formatter,
        result.instanceCount,
        isDuration
      );
    },
    labelColor: (tooltipItem, chart) => getTooltipLabelColor(tooltipItem, chart, visualization),
  };

  if (isPersistedTooltips) {
    tooltipCallbacks.title = () => '';
  } else if (groupedByDurationMaxValue) {
    tooltipCallbacks.title = (data, {labels}) => data.length && duration(labels[data[0].index]);
  }

  if (visualization === 'pie' && !isPersistedTooltips && !groupedByDurationMaxValue) {
    tooltipCallbacks.beforeLabel = ({index}, {labels}) => labels[index];
  }

  if (visualization === 'pie' && groupedByDurationMaxValue) {
    options.legend.labels.generateLabels = (chart) => {
      // we need to adjust the generate labels function to convert milliseconds to nicely formatted duration strings
      // taken and adjusted from https://github.com/chartjs/Chart.js/blob/2.9/src/controllers/controller.doughnut.js#L48-L66
      const data = chart.data;
      if (data.labels.length && data.datasets.length) {
        return data.labels.map(function (label, i) {
          const meta = chart.getDatasetMeta(0);
          const style = meta.controller.getStyle(i);

          return {
            text: duration(label),
            fillStyle: style.backgroundColor,
            strokeStyle: style.borderColor,
            lineWidth: style.borderWidth,
            hidden: isNaN(data.datasets[0].data[i]) || meta.data[i].hidden,

            // Extra data used for toggling the correct item
            index: i,
          };
        });
      }
      return [];
    };
  }

  return {
    ...options,
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    // plugin property
    showAllTooltips: isPersistedTooltips,
    tooltips: {
      ...(isPersistedTooltips && {
        yAlign: 'bottom',
        xAlign: 'center',
        displayColors: false,
      }),
      callbacks: tooltipCallbacks,
    },
  };
}

export function createBarOptions({
  targetValue,
  configuration,
  stacked,
  maxDuration,
  isDark,
  autoSkip,
  isPersistedTooltips,
  groupedByDurationMaxValue = false,
}) {
  const targetLine = targetValue && getFormattedTargetValue(targetValue);

  return {
    ...(configuration.pointMarkers === false ? {elements: {point: {radius: 0}}} : {}),
    legend: {display: false},
    layout: {
      padding: {top: isPersistedTooltips ? 30 : 0},
    },
    scales: {
      yAxes: [
        {
          gridLines: {
            color: getColorFor('grid', isDark),
          },
          scaleLabel: {
            display: !!configuration.yLabel,
            labelString: configuration.yLabel,
          },
          ticks: {
            ...(maxDuration ? createDurationFormattingOptions(targetLine, maxDuration) : {}),
            beginAtZero: true,
            fontColor: getColorFor('label', isDark),
            suggestedMax: targetLine,
          },
        },
      ],
      xAxes: [
        {
          gridLines: {
            color: getColorFor('grid', isDark),
          },
          scaleLabel: {
            display: !!configuration.xLabel,
            labelString: configuration.xLabel,
          },
          ticks: {
            fontColor: getColorFor('label', isDark),
            autoSkip,
            callback: function (label, idx, allLabels) {
              const width = this.maxWidth / allLabels.length;
              const widthPerCharacter = 7;

              if (stacked && label.length > width / widthPerCharacter) {
                return label.substr(0, Math.floor(width / widthPerCharacter)) + '…';
              }

              return label;
            },
            ...(groupedByDurationMaxValue
              ? createDurationFormattingOptions(false, groupedByDurationMaxValue)
              : {}),
          },
          stacked,
        },
      ],
    },
    spanGaps: true,
    // plugin property
    lineAt: targetLine,
  };
}

function createPieOptions(isDark) {
  return {
    legend: {
      display: true,
      labels: {fontColor: getColorFor('label', isDark)},
    },
  };
}

export function createDatasetOptions(type, data, targetValue, datasetColor, isStriped, isDark) {
  switch (type) {
    case 'pie':
      return {
        borderColor: getColorFor('border', isDark),
        backgroundColor: createColors(data.length, isDark),
        borderWidth: undefined,
      };
    case 'line':
      return {
        borderColor: datasetColor,
        backgroundColor: 'transparent',
        borderWidth: 2,
        legendColor: datasetColor,
      };
    case 'bar':
    case 'number':
      const barColor = targetValue
        ? determineBarColor(targetValue, data, datasetColor, isStriped, isDark)
        : datasetColor;
      return {
        borderColor: barColor,
        backgroundColor: barColor,
        legendColor: datasetColor,
        borderWidth: 1,
      };
    default:
      return {
        borderColor: undefined,
        backgroundColor: undefined,
        borderWidth: undefined,
      };
  }
}
