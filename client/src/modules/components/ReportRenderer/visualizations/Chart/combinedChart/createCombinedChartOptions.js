/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {isDurationReport, formatters} from 'services';

import {
  formatTooltip,
  formatTooltipTitle,
  getTooltipLabelColor,
  canBeInterpolated,
} from '../service';
import {createBarOptions} from '../defaultChart/createDefaultChartOptions';
import {getColorFor} from '../colorsUtils';
import {generateLegendLabels} from './service';

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
    label: ({dataset, dataIndex}) =>
      formatTooltip({
        dataset,
        dataIndex,
        configuration,
        formatter: dataset.formatter ?? formatter,
        instanceCount: result.instanceCount,
        isDuration,
        showLabel: !isNumber,
      }),
    labelColor: (tooltipItem) => getTooltipLabelColor(tooltipItem, visualization),
    title: (tooltipItems) =>
      formatTooltipTitle(tooltipItems?.[0]?.label, tooltipItems?.[0]?.chart.chartArea.width),
  };

  if (isPersistedTooltips) {
    tooltipCallbacks.title = () => '';
    tooltipCallbacks.afterTitle = () => '';
  } else if (groupedByDurationMaxValue) {
    tooltipCallbacks.title = (tooltipItems) =>
      tooltipItems?.[0]?.label && formatters.duration(tooltipItems[0].label);
  }

  return {
    ...createBarOptions({
      targetValue,
      visualization,
      configuration,
      maxDuration,
      isDark,
      isPersistedTooltips,
      measures: result.measures,
      entity: view.entity,
      autoSkip: canBeInterpolated(groupBy),
      groupedByDurationMaxValue,
      isCombined: true,
    }),
    plugins: {
      legend: {
        display: true,
        labels: {
          generateLabels: generateLegendLabels,
          color: getColorFor('label', isDark),
        },
        // prevent hiding datasets when clicking on their legends
        onClick: (e) => e.native.stopPropagation(),
      },
      tooltip: {
        enabled: !isPersistedTooltips,
        callbacks: tooltipCallbacks,
      },
      datalabels: {
        display: isPersistedTooltips,
        formatter: (_, {dataset, dataIndex}) =>
          formatTooltip({
            dataset,
            dataIndex,
            configuration,
            formatter: dataset.formatter ?? formatter,
            instanceCount: result.instanceCount,
            isDuration,
            showLabel: false,
          }),
      },
    },
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    // plugin property
    showAllTooltips: isPersistedTooltips,
  };
}

function findMaxDurationAcrossReports(result, durationKey = 'value') {
  let maxDurations;
  if (result.measures?.length > 1) {
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
