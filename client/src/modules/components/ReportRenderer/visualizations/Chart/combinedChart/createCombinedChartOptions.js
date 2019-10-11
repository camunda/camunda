/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {generateLegendLabels} from './service';
import {formatTooltip, getTooltipLabelColor, canBeInterpolated} from '../service';
import {createBarOptions} from '../defaultChart/createDefaultChartOptions';
import {isDurationReport} from 'services';

export default function createCombinedChartOptions({report, targetValue, theme, formatter}) {
  const {
    data: {visualization, configuration},
    result
  } = report;

  const {groupBy} = Object.values(result.data)[0].data;

  const isDark = theme === 'dark';
  const isNumber = visualization === 'number';
  const instanceCountArr = Object.values(result.data).map(report => report.result.instanceCount);
  const isDuration = isDurationReport(Object.values(result.data)[0]);
  const maxDuration = isDuration ? findMaxDurationAcrossReports(result) : 0;
  const isPersistedTooltips = isDuration
    ? configuration.alwaysShowAbsolute
    : configuration.alwaysShowAbsolute || configuration.alwaysShowRelative;

  return {
    ...createBarOptions({
      targetValue,
      configuration,
      stacked: isNumber,
      maxDuration,
      isDark,
      isPersistedTooltips,
      autoSkip: canBeInterpolated(groupBy)
    }),
    legend: {
      display: true,
      labels: {
        generateLabels: generateLegendLabels
      },
      // prevent hiding datasets when clicking on their legends
      onClick: e => e.stopPropagation()
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
        displayColors: false
      }),
      callbacks: {
        ...(isPersistedTooltips && {title: () => ''}),
        label: (tooltipItem, data) => {
          return formatTooltip(
            tooltipItem,
            data,
            targetValue,
            configuration,
            formatter,
            instanceCountArr,
            isDuration || isNumber,
            visualization
          );
        },
        labelColor: (tooltipItem, chart) => getTooltipLabelColor(tooltipItem, chart, visualization)
      }
    }
  };
}

function findMaxDurationAcrossReports(result) {
  const reportsMaxDurations = Object.values(result.data).map(report => {
    if (typeof report.result.data === 'number') {
      return report.result.data;
    }
    if (report.result.data === null) {
      return 0;
    }
    return Math.max(...Object.values(report.result.data).map(({value}) => value));
  });

  return Math.max(...reportsMaxDurations);
}
