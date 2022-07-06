/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ColorPicker} from 'components';
import {isDurationReport, formatters} from 'services';
import {t} from 'translation';

import {getFormattedTargetValue} from './service';
import {
  formatTooltip,
  formatTooltipTitle,
  getTooltipLabelColor,
  canBeInterpolated,
} from '../service';
import {getColorFor, determineBarColor} from '../colorsUtils';

const {createDurationFormattingOptions, duration} = formatters;

export default function createDefaultChartOptions({report, targetValue, theme, formatter}) {
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
      options = createPieOptions(isDark, groupBy?.type === 'duration');
      break;
    case 'line':
    case 'bar':
    case 'barLine':
      options = createBarOptions({
        targetValue,
        visualization,
        configuration,
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
    label: ({dataset, dataIndex}) =>
      formatTooltip({
        dataset,
        dataIndex,
        configuration,
        formatter: dataset.formatter ?? formatter,
        instanceCount: result.instanceCount,
        isDuration,
        showLabel: true,
      }),
    labelColor: (tooltipItem) => getTooltipLabelColor(tooltipItem, visualization),
    title: (tooltipItems) =>
      formatTooltipTitle(tooltipItems?.[0]?.label, tooltipItems?.[0]?.chart.chartArea.width),
  };

  if (isPersistedTooltips) {
    tooltipCallbacks.title = () => '';
  } else if (groupedByDurationMaxValue) {
    tooltipCallbacks.title = (tooltipItems) =>
      tooltipItems?.[0]?.label && duration(tooltipItems[0].label);
  }

  if (visualization === 'pie' && !isPersistedTooltips && !groupedByDurationMaxValue) {
    tooltipCallbacks.title = (tooltipItems) => {
      return formatTooltipTitle(tooltipItems?.[0].label, tooltipItems?.[0]?.chart.chartArea.width);
    };
  }

  return {
    ...options,
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    plugins: {
      ...options.plugins,
      tooltip: {
        enabled: !isPersistedTooltips,
        callbacks: tooltipCallbacks,
      },
      datalabels: {
        ...options.plugins.datalabels,
        display: isPersistedTooltips,
        formatter: (_, {dataset, dataIndex}) =>
          formatTooltip({
            dataset,
            dataIndex,
            configuration,
            formatter: dataset.formatter ?? formatter,
            instanceCount: result.instanceCount,
            isDuration,
            showLabel: true,
          }),
      },
    },
  };
}

export function createBarOptions({
  targetValue,
  configuration,
  maxDuration,
  isDark,
  autoSkip,
  isPersistedTooltips,
  measures = [],
  entity,
  groupedByDurationMaxValue = false,
  isCombined,
  visualization,
}) {
  const isCombinedNumber = isCombined && visualization === 'number';
  const stacked =
    configuration.stackedBar && isCombined && ['bar', 'barLine'].includes(visualization);
  const targetLine = !stacked && targetValue && getFormattedTargetValue(targetValue);
  const hasMultipleAxes = ['frequency', 'duration'].every((prop) =>
    measures.some(({property}) => property === prop)
  );
  const axisType = configuration.logScale ? 'logarithmic' : undefined;

  const yAxes = {
    'axis-0': {
      type: axisType,
      grid: {
        color: getColorFor('grid', isDark),
      },
      title: {
        display: !!configuration.yLabel,
        text: configuration.yLabel,
        color: getColorFor('label', isDark),
        font: {
          size: 14,
          weight: 'bold',
        },
      },
      ticks: {
        ...(maxDuration && !hasMultipleAxes
          ? createDurationFormattingOptions(targetLine, maxDuration, configuration.logScale)
          : {}),
        beginAtZero: true,
        color: getColorFor('label', isDark),
      },
      suggestedMax: targetLine,
      id: 'axis-0',
      axis: 'y',
      stacked,
    },
  };

  if (hasMultipleAxes) {
    yAxes['axis-0'].title.display = true;
    yAxes['axis-0'].title.text = `${t('common.' + entity + '.label')} ${t('report.view.count')}`;

    yAxes['axis-1'] = {
      type: axisType,
      grid: {
        drawOnChartArea: false,
      },
      title: {
        display: true,
        text: `${t('common.' + entity + '.label')} ${t('report.view.duration')}`,
        color: getColorFor('label', isDark),
        font: {
          size: 14,
          weight: 'bold',
        },
      },
      ticks: {
        ...createDurationFormattingOptions(targetLine, maxDuration, configuration.logScale),
        beginAtZero: true,
        color: getColorFor('label', isDark),
      },
      suggestedMax: targetLine,
      position: 'right',
      id: 'axis-1',
      axis: 'y',
    };
  }

  return {
    ...(configuration.pointMarkers === false ? {elements: {point: {radius: 0}}} : {}),
    layout: {
      padding: {top: isPersistedTooltips ? 30 : 0},
    },
    scales: {
      ...yAxes,
      xAxes: {
        axis: 'x',
        grid: {
          color: getColorFor('grid', isDark),
        },
        title: {
          display: !!configuration.xLabel,
          text: configuration.xLabel,
          color: getColorFor('label', isDark),
          font: {
            size: 14,
            weight: 'bold',
          },
        },
        ticks: {
          color: getColorFor('label', isDark),
          autoSkip,
          callback: function (value, idx, allLabels) {
            const label = this.getLabelForValue(value);
            const width = this.maxWidth / allLabels.length;
            const widthPerCharacter = 7;

            if (isCombinedNumber && label.length > width / widthPerCharacter) {
              return label.substr(0, Math.floor(width / widthPerCharacter)) + '…';
            }

            return label;
          },
          ...(groupedByDurationMaxValue
            ? createDurationFormattingOptions(false, groupedByDurationMaxValue)
            : {}),
        },
        stacked: stacked || isCombinedNumber,
      },
    },
    spanGaps: true,
    // plugin property
    lineAt: targetLine,
    tension: 0.4,
    plugins: {
      legend: {
        display: measures.length > 1,
        onClick: (e) => e.native.stopPropagation(),
        labels: {
          color: getColorFor('label', isDark),
          boxWidth: 12,
          // make sorting only by dataset index to ignore 'order' dataset option
          sort: (a, b) => a.datasetIndex - b.datasetIndex,
        },
      },
    },
  };
}

function createPieOptions(isDark, isGroupedByDuration) {
  const generateLabels = (chart) => {
    // we need to adjust the generate labels function to convert milliseconds to nicely formatted duration strings
    // we also need it to render the legends based on the hovered dataset in order to fade out only non hovered legends
    // taken and adjusted from https://github.com/chartjs/Chart.js/blob/2.9/src/controllers/controller.doughnut.js#L48-L66
    const data = chart.data;
    let labels = data.labels;

    if (data.labels.length && data.datasets.length) {
      return labels.map(function (label, i) {
        // the 'hovered' property is provided by a plugin
        const hoveredDatasetIdx = data.datasets.findIndex((dataset) => dataset.hovered);
        const meta = chart.getDatasetMeta(hoveredDatasetIdx > 0 ? hoveredDatasetIdx : 0);
        const style = meta.controller.getStyle(i);

        return {
          text: isGroupedByDuration ? duration(label) : label,
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

  return {
    emptyBackgroundColor: getColorFor('emptyPie', isDark),
    plugins: {
      legend: {
        display: true,
        autoCollapse: true,
        labels: {color: getColorFor('label', isDark), generateLabels},
      },
      datalabels: {
        align: 'start',
      },
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
  stackedBar,
}) {
  let color = datasetColor;
  let legendColor = datasetColor;
  if (measureCount > 1) {
    legendColor = color = ColorPicker.getGeneratedColors(measureCount)[datasetIdx];
  } else if (['bar', 'number'].includes(type) && !stackedBar && targetValue) {
    color = determineBarColor(targetValue, data, datasetColor, isStriped, isDark);
    legendColor = datasetColor;
  }

  switch (type) {
    case 'pie':
      const pieColors = ColorPicker.getGeneratedColors(data.length);
      return {
        borderColor: getColorFor('border', isDark),
        backgroundColor: pieColors,
        hoverBackgroundColor: pieColors,
        borderWidth: undefined,
      };
    case 'line':
      return {
        borderColor: color,
        backgroundColor: color,
        fill: false,
        borderWidth: 2,
        legendColor: color,
        type: 'line',
      };
    case 'bar':
    case 'number':
      return {
        borderColor: color,
        backgroundColor: color,
        hoverBackgroundColor: color,
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
