/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isDurationReport, formatters} from 'services';

import {generateLegendLabels} from './service';
import {formatTooltip, getTooltipLabelColor, canBeInterpolated} from '../service';
import {createBarOptions} from '../defaultChart/createDefaultChartOptions';

export default function createCombinedChartOptions({report, targetValue, theme, formatter}) {
  const {
    data: {visualization, configuration},
    result,
  } = report;

  const {view, groupBy} = Object.values(result.data)[0].data;

  const isDark = theme === 'dark';
  const isNumber = visualization === 'number';
  const isDuration = isDurationReport(Object.values(result.data)[0]);
  const maxDuration = isDuration ? findMaxDurationAcrossReports(result) : 0;
  const isPersistedTooltips = isDuration
    ? configuration.alwaysShowAbsolute
    : configuration.alwaysShowAbsolute || configuration.alwaysShowRelative;

  const groupedByDurationMaxValue =
    groupBy?.type === 'duration' && findMaxDurationAcrossReports(result, 'label');

  const tooltipCallbacks = {
    label: (tooltipItem, data) => {
      return formatTooltip(
        tooltipItem,
        data,
        configuration,
        data.datasets[tooltipItem.datasetIndex].formatter ?? formatter,
        result.instanceCount,
        isDuration
      );
    },
    labelColor: (tooltipItem, chart) => getTooltipLabelColor(tooltipItem, chart, visualization),
    afterTitle: (data, {datasets}) =>
      data.length && datasets[data[data.length - 1].datasetIndex].label,
  };

  if (isPersistedTooltips) {
    tooltipCallbacks.title = () => '';
    tooltipCallbacks.afterTitle = () => '';
  } else if (groupedByDurationMaxValue) {
    tooltipCallbacks.title = (data, {labels}) =>
      data.length && formatters.duration(labels[data[0].index]);
  }

  return {
    ...createBarOptions({
      targetValue,
      configuration,
      stacked: isNumber,
      maxDuration,
      isDark,
      isPersistedTooltips,
      measures: result.measures,
      entity: view.entity,
      autoSkip: canBeInterpolated(groupBy),
      groupedByDurationMaxValue,
    }),
    legend: {
      display: true,
      labels: {
        generateLabels: generateLegendLabels,
      },
      // prevent hiding datasets when clicking on their legends
      onClick: (e) => e.stopPropagation(),
    },
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

function findMaxDurationAcrossReports(result, durationKey = 'value') {
  let maxDurations;
  if (result.measures.length > 1) {
    const measuresDurationValues = result.measures
      .filter(({property}) => property === 'duration')
      .map((measure) => measure.data.map(({value}) => value))
      .flat();

    maxDurations = measuresDurationValues.map((valueArr) =>
      Math.max(...valueArr.map((entry) => entry[durationKey]))
    );
  } else {
    maxDurations = Object.values(result.data).map((report) => {
      if (typeof report.result.data === 'number') {
        return report.result.data;
      }
      if (report.result.data === null) {
        return 0;
      }
      return Math.max(...Object.values(report.result.data).map((entry) => entry[durationKey]));
    });
  }

  return Math.max(...maxDurations);
}
