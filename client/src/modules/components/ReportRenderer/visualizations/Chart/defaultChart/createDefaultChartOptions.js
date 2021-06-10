/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ColorPicker} from 'components';
import {isDurationReport, formatters} from 'services';
import {t} from 'translation';

import {getFormattedTargetValue} from './service';
import {formatTooltip, getTooltipLabelColor, canBeInterpolated} from '../service';
import {getColorFor, determineBarColor} from '../colorsUtils';

const {createDurationFormattingOptions, duration} = formatters;

export default function createDefaultChartOptions({report, targetValue, theme}) {
  const {
    data: {visualization, view, groupBy, configuration, definitions},
    result,
  } = report;

  const decisionDefinitionKey = definitions?.[0].key;

  const isDark = theme === 'dark';
  const isDuration = isDurationReport(report);
  const maxValue = isDuration
    ? Math.max(
        ...result.measures
          .filter(({property}) => property === 'duration')
          .map((measure) => measure.data.map(({value}) => value))
          .flat()
      )
    : 0;
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
        measures: result.measures,
        entity: view.entity,
        autoSkip: canBeInterpolated(groupBy, configuration.xml, decisionDefinitionKey),
      });
      break;
    default:
      options = {};
  }

  const tooltipCallbacks = {
    label: (tooltipItem, data) => {
      const {label, formatter} = data.datasets[tooltipItem.datasetIndex];
      return (
        label +
        ': ' +
        formatTooltip(tooltipItem, data, configuration, formatter, result.instanceCount, isDuration)
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
    tooltipCallbacks.title = (data, {labels}) => data.length && labels[data[0].index];
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
  measures = [],
  entity,
  groupedByDurationMaxValue = false,
}) {
  const targetLine = targetValue && getFormattedTargetValue(targetValue);
  const hasMultipleAxes = ['frequency', 'duration'].every((prop) =>
    measures.some(({property}) => property === prop)
  );

  const yAxes = [
    {
      gridLines: {
        color: getColorFor('grid', isDark),
      },
      scaleLabel: {
        display: !!configuration.yLabel,
        labelString: configuration.yLabel,
        fontStyle: 'bold',
      },
      ticks: {
        ...(maxDuration && !hasMultipleAxes
          ? createDurationFormattingOptions(targetLine, maxDuration)
          : {}),
        beginAtZero: true,
        fontColor: getColorFor('label', isDark),
        suggestedMax: targetLine,
      },
      id: 'axis-0',
    },
  ];

  if (hasMultipleAxes) {
    yAxes[0].scaleLabel = {
      display: true,
      labelString: `${t('common.' + entity + '.label')} ${t('report.view.count')}`,
      fontStyle: 'bold',
    };

    yAxes.push({
      gridLines: {
        drawOnChartArea: false,
      },
      scaleLabel: {
        display: true,
        labelString: `${t('common.' + entity + '.label')} ${t('report.view.duration')}`,
        fontStyle: 'bold',
      },
      ticks: {
        ...createDurationFormattingOptions(targetLine, maxDuration),
        beginAtZero: true,
        fontColor: getColorFor('label', isDark),
        suggestedMax: targetLine,
      },
      position: 'right',
      id: 'axis-1',
    });
  }

  return {
    ...(configuration.pointMarkers === false ? {elements: {point: {radius: 0}}} : {}),
    legend: {
      display: measures.length > 1,
      onClick: (e) => e.stopPropagation(),
      labels: {
        boxWidth: 12,
      },
    },
    layout: {
      padding: {top: isPersistedTooltips ? 30 : 0},
    },
    scales: {
      yAxes,
      xAxes: [
        {
          gridLines: {
            color: getColorFor('grid', isDark),
          },
          scaleLabel: {
            display: !!configuration.xLabel,
            labelString: configuration.xLabel,
            fontStyle: 'bold',
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
    emptyBackgroundColor: getColorFor('emptyPie', isDark),
    legend: {
      display: true,
      labels: {fontColor: getColorFor('label', isDark)},
    },
  };
}

export function createDatasetOptions({
  type,
  data,
  targetValue,
  datasetColor,
  isStriped,
  isDark,
  measureCount = 1,
  datasetIdx = 0,
}) {
  let color = datasetColor;
  let legendColor = datasetColor;
  if (measureCount > 1) {
    legendColor = color = ColorPicker.getGeneratedColors(measureCount)[datasetIdx];
  } else if (['bar', 'number'].includes(type) && targetValue) {
    color = determineBarColor(targetValue, data, datasetColor, isStriped, isDark);
    legendColor = datasetColor;
  }

  switch (type) {
    case 'pie':
      return {
        borderColor: getColorFor('border', isDark),
        backgroundColor: ColorPicker.getGeneratedColors(data.length),
        borderWidth: undefined,
      };
    case 'line':
      return {
        borderColor: color,
        backgroundColor: color,
        fill: false,
        borderWidth: 2,
        legendColor: color,
      };
    case 'bar':
    case 'number':
      return {
        borderColor: color,
        backgroundColor: color,
        legendColor,
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
