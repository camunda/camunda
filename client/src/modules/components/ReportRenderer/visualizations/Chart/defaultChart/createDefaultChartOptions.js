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
  hasReportPersistedTooltips,
  getAxesConfig,
} from '../service';
import {getColorFor, determineBarColor} from '../colorsUtils';

const {createDurationFormattingOptions, duration} = formatters;

export default function createDefaultChartOptions({report, targetValue, theme, formatter}) {
  const {
    data: {visualization, view, groupBy, configuration, definitions},
    result,
  } = report;
  const {precision, xml} = configuration;

  const decisionDefinitionKey = definitions?.[0].key;

  const isDark = theme === 'dark';
  const isDuration = isDurationReport(report);
  const isPersistedTooltips = hasReportPersistedTooltips(report);

  const groupedByDurationMaxValue =
    groupBy?.type === 'duration' && Math.max(...result.data.map(({label}) => +label));

  let options;
  switch (visualization) {
    case 'pie':
      options = createPieOptions(isDark, groupBy?.type === 'duration', precision);
      break;
    case 'line':
    case 'bar':
    case 'barLine':
      options = createBarOptions({
        targetValue,
        visualization,
        configuration,
        maxDuration: getMaxDuration(result, isDuration),
        groupedByDurationMaxValue,
        isDark,
        isPersistedTooltips,
        measures: result.measures,
        entity: view.entity,
        autoSkip: canBeInterpolated(groupBy, xml, decisionDefinitionKey),
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
      tooltipItems?.[0]?.label && duration(tooltipItems[0].label, precision);
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
  const {stackedBar, xLabel, yLabel, logScale, pointMarkers, horizontalBar} = configuration;
  const isCombinedNumber = isCombined && visualization === 'number';
  const stacked = stackedBar && isCombined && ['bar', 'barLine'].includes(visualization);
  const targetLine = !stacked && targetValue && getFormattedTargetValue(targetValue);
  const hasMultipleAxes = ['frequency', 'duration'].every((prop) =>
    measures.some(({property}) => property === prop)
  );
  const hasCountMeasure = measures.some(({property}) => property === 'frequency');
  const topPadding = isPersistedTooltips && !horizontalBar;
  const {axis0, axis1, groupBy} = getAxesConfig(horizontalBar);

  const measuresAxis = {
    'axis-0': {
      grid: {
        color: getColorFor('grid', isDark),
      },
      title: {
        display: !!yLabel,
        text: yLabel,
        color: getColorFor('label', isDark),
        font: {
          size: 14,
          weight: 'bold',
        },
      },
      ticks: {
        ...(maxDuration && !hasMultipleAxes
          ? createDurationFormattingOptions(targetLine, maxDuration, logScale)
          : {}),
        beginAtZero: true,
        color: getColorFor('label', isDark),
        precision: hasCountMeasure ? 0 : undefined,
      },
      suggestedMax: targetLine,
      ...axis0,
      stacked,
    },
  };

  if (hasMultipleAxes) {
    measuresAxis['axis-0'].title.display = true;
    measuresAxis['axis-0'].title.text = `${t('common.' + entity + '.label')} ${t(
      'report.view.count'
    )}`;

    measuresAxis['axis-1'] = {
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
        ...createDurationFormattingOptions(targetLine, maxDuration, logScale),
        beginAtZero: true,
        color: getColorFor('label', isDark),
      },
      suggestedMax: targetLine,
      ...axis1,
    };
  }

  const groupByAxis = {
    grid: {
      color: getColorFor('grid', isDark),
    },
    title: {
      display: !!xLabel,
      text: xLabel,
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
    ...groupBy,
  };

  if (logScale) {
    Object.keys(measuresAxis).forEach((key) => {
      measuresAxis[key].type = 'logarithmic';
    });
  }

  return {
    ...(pointMarkers === false ? {elements: {point: {radius: 0}}} : {}),
    indexAxis: groupBy.id,
    layout: {
      padding: {top: topPadding ? 30 : 0},
    },
    scales: {
      ...measuresAxis,
      [groupBy.id]: groupByAxis,
    },
    spanGaps: true,
    // plugin property
    lineAt: targetLine,
    tension: 0.4,
    plugins: {
      datalabels: {
        align: (context) => {
          if (!horizontalBar) {
            return 'end';
          }

          const scale = context.chart.scales[context.dataset.xAxisID];
          const yPosition = scale.getPixelForValue(context.dataset.data[context.dataIndex]);

          const {left, right} = context.chart.chartArea;
          const center = left + 0.7 * (right - left);

          return yPosition > center ? 'start' : 'end';
        },
      },
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

function createPieOptions(isDark, isGroupedByDuration, precision) {
  const generateLabels = (chart) => {
    // we need to adjust the generate labels function to convert milliseconds to nicely formatted duration strings
    // we also need it to render the legends based on the hovered dataset in order to fade out only non hovered legends
    // taken and adjusted from https://github.com/chartjs/Chart.js/blob/2.9/src/controllers/controller.doughnut.js#L48-L66
    const {data, legend} = chart;
    const {color} = legend.options.labels;
    let labels = data.labels;

    if (data.labels.length && data.datasets.length) {
      return labels.map(function (label, i) {
        // the 'hovered' property is provided by a plugin
        const hoveredDatasetIdx = data.datasets.findIndex((dataset) => dataset.hovered);
        const meta = chart.getDatasetMeta(hoveredDatasetIdx > 0 ? hoveredDatasetIdx : 0);
        const style = meta.controller.getStyle(i);

        return {
          text: isGroupedByDuration ? duration(label, precision) : label,
          fillStyle: style.backgroundColor,
          strokeStyle: style.borderColor,
          lineWidth: style.borderWidth,
          hidden: isNaN(data.datasets[0].data[i]) || meta.data[i].hidden,
          fontColor: color,
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

function getMaxDuration(result, isDuration) {
  if (!isDuration) {
    return 0;
  }

  return Math.max(
    ...result.measures
      .filter(({property}) => property === 'duration')
      .map((measure) => measure.data.map(({value}) => value))
      .flat(),
    0
  );
}
