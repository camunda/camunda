/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import update from 'immutability-helper';
import structuredClone from '@ungap/structured-clone';

import {t} from 'translation';
import {getVariableLabel} from 'variables';

import {isCategoricalBar, isCategorical} from '../reportService';

import * as processOptions from './process';
import * as decisionOptions from './decision';

const reportOptionsMap = {
  process: processOptions,
  decision: decisionOptions,
};

export function createReportUpdate(reportType, report, updateType, newValue, payloadAdjustment) {
  const reportOptions = reportOptionsMap[reportType];
  let newPayload = reportOptions[updateType].find(({key}) => key === newValue).payload(report);

  if (payloadAdjustment) {
    newPayload = update(newPayload, payloadAdjustment);
  }

  let newReport = {...structuredClone(report), ...newPayload};

  // ensure group is still valid
  const oldGroup = reportOptions.group.find(({matcher}) => matcher(newReport));
  if (!oldGroup?.visible(newReport) || !oldGroup?.enabled(newReport)) {
    const possibleGroups = reportOptions.group
      .filter(({visible, enabled}) => visible(newReport) && enabled(newReport))
      .sort((a, b) => a.priority - b.priority);

    newReport = {...newReport, ...possibleGroups[0]?.payload(newReport)};
  }

  // ensure distribution is still valid
  if (reportType === 'process') {
    const oldDistribution = reportOptions.distribution.find(({matcher}) => matcher(newReport));
    if (
      !oldDistribution?.visible(newReport) ||
      !oldDistribution?.enabled(newReport) ||
      (updateType === 'view' &&
        reportOptions.view.find(({matcher}) => matcher(report)) !==
          reportOptions.view.find(({matcher}) => matcher(newReport)) &&
        oldDistribution.key === 'none') || // try to find distribution when switching view
      (updateType === 'group' &&
        ['flowNodes', 'userTasks'].includes(
          reportOptions.group.find(({matcher}) => matcher(report))?.key
        )) // try to find distribution when switching away from flowNodes
    ) {
      const possibleDistributions = reportOptions.distribution
        .filter(({visible, enabled}) => visible(newReport) && enabled(newReport))
        .sort((a, b) => a.priority - b.priority);

      newReport = {...newReport, ...possibleDistributions[0].payload(newReport)};
    }
  }

  // ensure visualization is still valid
  const oldVisualization = reportOptions.visualization.find(({matcher}) => matcher(newReport));
  if (!oldVisualization?.visible(newReport) || !oldVisualization?.enabled(newReport)) {
    const possibleVisualizations = reportOptions.visualization
      .filter(({visible, enabled}) => visible(newReport) && enabled(newReport))
      .sort((a, b) => a.priority - b.priority);

    newReport = {...newReport, ...possibleVisualizations[0].payload(newReport)};
  }

  // --- ensure configuration is still valid ---
  // update y label on view change
  if (updateType === 'view' && ['duration', 'frequency'].includes(newReport.view.properties[0])) {
    let label = reportOptions.view.find(({matcher}) => matcher(newReport)).label() + ' ';
    if (reportType === 'process') {
      if (newReport.view.properties[0] === 'frequency') {
        label += t('report.view.count');
      } else if (newReport.view.properties[0] === 'duration') {
        label += t('report.view.duration');
      }
    }
    newReport.configuration.yLabel = label;
  }

  // update x label on group and view update
  if (['view', 'group'].includes(updateType)) {
    if (['variable', 'inputVariable', 'outputVariable'].includes(newReport.groupBy.type)) {
      const {name, type} = newReport.groupBy.value;
      newReport.configuration.xLabel = getVariableLabel(name, type);
    } else {
      newReport.configuration.xLabel = reportOptions.group
        .find(({matcher}) => matcher(newReport))
        .label();
    }
  }

  // set default sorting if not a sorting update
  if (updateType !== 'sortingOrder') {
    newReport.configuration.sorting = getDefaultSorting({reportType, data: newReport});
  }

  newReport.configuration.horizontalBar = isCategoricalBar(newReport);

  // reset tablecolumnorder
  newReport.configuration.tableColumns.columnOrder = [];

  if (reportType === 'process') {
    // disable and reset heatmap target values if no heatmap target values are allowed
    if (!isDurationHeatmap(newReport)) {
      newReport.configuration.heatmapTargetValue = {active: false, values: {}};
    }

    // remove sum aggregation from incident view
    if (newReport.view.entity === 'incident') {
      newReport.configuration.aggregationTypes = newReport.configuration.aggregationTypes.filter(
        (agg) => agg.type !== 'sum'
      );

      if (newReport.configuration.aggregationTypes.length === 0) {
        newReport.configuration.aggregationTypes = [{type: 'avg', value: null}];
      }
    }

    // remove percentile aggregation from group by process
    if (newReport.distributedBy.type === 'process') {
      newReport.configuration.aggregationTypes = newReport.configuration.aggregationTypes.filter(
        (agg) => agg.type !== 'percentile'
      );

      if (newReport.configuration.aggregationTypes.length === 0) {
        newReport.configuration.aggregationTypes = [{type: 'avg', value: null}];
      }
    }

    // remove percentage measure if it is not supported
    if (
      newReport.view.properties.includes('percentage') &&
      newReport.view.entity !== 'processInstance'
    ) {
      newReport.view.properties = newReport.view.properties.filter(
        (measure) => measure !== 'percentage'
      );
      if (newReport.view.properties.length === 0) {
        newReport.view.properties = ['frequency'];
      }
    }

    // remove process part if its not allowed
    if (!isProcessInstanceDuration(newReport) || newReport.definitions.length > 1) {
      newReport.configuration.processPart = null;
    }

    // remove goals for multi-measure reports
    if (newReport.view.properties.length > 1) {
      newReport.configuration.targetValue.active = false;
    }

    // disable bucket size config on group update
    // reason: group by variable bucket size does not make sense for group by duration
    if (updateType === 'group') {
      newReport.configuration.customBucket.active = false;
      newReport.configuration.distributeByCustomBucket.active = false;
    }
  }

  return {
    view: {$set: newReport.view},
    groupBy: {$set: newReport.groupBy},
    distributedBy: {$set: newReport.distributedBy},
    visualization: {$set: newReport.visualization},
    configuration: {$set: newReport.configuration},
  };
}

function isDurationHeatmap({view, visualization, definitions}) {
  return (
    view &&
    (view.entity === 'flowNode' || view.entity === 'userTask') &&
    view.properties[0] === 'duration' &&
    visualization === 'heat' &&
    definitions?.[0].key &&
    definitions?.[0].versions?.length > 0
  );
}

function isProcessInstanceDuration({view}) {
  return view && view.entity === 'processInstance' && view.properties[0] === 'duration';
}

export function getDefaultSorting({reportType, data}) {
  const {view, groupBy, visualization} = data;
  if (visualization === 'table' && ['flowNodes', 'userTasks'].includes(groupBy?.type)) {
    return {by: 'label', order: 'asc'};
  }

  if ((view?.properties?.[0] ?? view?.property) === 'rawData') {
    const by = reportType === 'process' ? 'startDate' : 'evaluationDateTime';
    return {by, order: 'desc'};
  }

  if (visualization !== 'table' && isCategorical(data)) {
    return {by: 'value', order: 'desc'};
  }

  if (groupBy?.type.toLowerCase().includes('variable')) {
    // Descending for Date and Boolean
    // Ascending for Integer, Double, Long
    const order = ['Date', 'Boolean'].includes(groupBy.value.type) ? 'desc' : 'asc';
    return {by: 'key', order};
  }

  if (groupBy?.type.toLowerCase().includes('date')) {
    return {by: 'key', order: 'asc'};
  }

  return {by: 'key', order: 'desc'};
}
