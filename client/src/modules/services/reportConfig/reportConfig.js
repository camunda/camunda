/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import update from 'immutability-helper';

import {t} from 'translation';
import {getVariableLabel} from 'variables';

import * as processOptions from './process';
import * as decisionOptions from './decision';

const config = {
  process: processOptions,
  decision: decisionOptions,
};

export function createReportUpdate(reportType, report, type, newValue, payloadAdjustment) {
  const options = config[reportType];
  let newPayload = options[type].find(({key}) => key === newValue).payload(report);

  if (payloadAdjustment) {
    newPayload = update(newPayload, payloadAdjustment);
  }

  let newReport = {...report, ...newPayload};

  // ensure group is still valid
  const oldGroup = options.group.find(({matcher}) => matcher(newReport));
  if (!oldGroup || !oldGroup.visible(newReport) || !oldGroup.enabled(newReport)) {
    const possibleGroups = options.group
      .filter(({visible, enabled}) => visible(newReport) && enabled(newReport))
      .sort((a, b) => a.priority - b.priority);

    newReport = {...newReport, ...possibleGroups[0].payload(newReport)};
  }

  // ensure distribution is still valid
  if (reportType === 'process') {
    const oldDistribution = options.distribution.find(({matcher}) => matcher(newReport));
    if (
      !oldDistribution ||
      !oldDistribution.visible(newReport) ||
      !oldDistribution.enabled(newReport) ||
      (type === 'view' &&
        options.view.find(({matcher}) => matcher(report)) !==
          options.view.find(({matcher}) => matcher(newReport)) &&
        oldDistribution.key === 'none') || // try to find distribution when switching view
      (type === 'group' &&
        ['flowNodes', 'userTasks'].includes(
          options.group.find(({matcher}) => matcher(report))?.key
        )) // try to find distribution when switching away from flowNodes
    ) {
      const possibleDistributions = options.distribution
        .filter(({visible, enabled}) => visible(newReport) && enabled(newReport))
        .sort((a, b) => a.priority - b.priority);

      newReport = {...newReport, ...possibleDistributions[0].payload(newReport)};
    }
  }

  // ensure visualization is still valid
  const oldVisualization = options.visualization.find(({matcher}) => matcher(newReport));
  if (
    !oldVisualization ||
    !oldVisualization.visible(newReport) ||
    !oldVisualization.enabled(newReport)
  ) {
    const possibleVisualizations = options.visualization
      .filter(({visible, enabled}) => visible(newReport) && enabled(newReport))
      .sort((a, b) => a.priority - b.priority);

    newReport = {...newReport, ...possibleVisualizations[0].payload(newReport)};
  }

  // --- ensure configuration is still valid ---
  // update y label on view change
  if (type === 'view' && ['duration', 'frequency'].includes(newReport.view.properties[0])) {
    let label = options.view.find(({matcher}) => matcher(newReport)).label() + ' ';
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
  if (['view', 'group'].includes(type)) {
    if (['variable', 'inputVariable', 'outputVariable'].includes(newReport.groupBy.type)) {
      const {name, type} = newReport.groupBy.value;
      newReport.configuration.xLabel = getVariableLabel(name, type);
    } else {
      newReport.configuration.xLabel = options.group
        .find(({matcher}) => matcher(newReport))
        .label();
    }
  }

  // update sorting and tablecolumnorder
  report.configuration.sorting = getDefaultSorting({reportType, data: newReport});
  report.configuration.tableColumns.columnOrder = [];

  if (reportType === 'process') {
    // disable and reset heatmap target values if no heatmap target values are allowed
    if (!isDurationHeatmap(newReport)) {
      newReport.configuration.heatmapTargetValue = {active: false, values: {}};
    }

    // remove sum aggregation from incident view
    if (newReport.view.entity === 'incident') {
      newReport.configuration.aggregationTypes = newReport.configuration.aggregationTypes.filter(
        (type) => type !== 'sum'
      );

      if (newReport.configuration.aggregationTypes.length === 0) {
        newReport.configuration.aggregationTypes = ['avg'];
      }
    }

    // remove median aggregation from group by process
    if (newReport.distributedBy.type === 'process') {
      newReport.configuration.aggregationTypes = newReport.configuration.aggregationTypes.filter(
        (type) => type !== 'median'
      );

      if (newReport.configuration.aggregationTypes.length === 0) {
        newReport.configuration.aggregationTypes = ['avg'];
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
    if (type === 'group') {
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

function getDefaultSorting({reportType, data: {view, groupBy, visualization}}) {
  if (visualization !== 'table') {
    return null;
  }

  if ((view?.properties?.[0] ?? view?.property) === 'rawData') {
    const by = reportType === 'process' ? 'startDate' : 'evaluationDateTime';
    return {by, order: 'desc'};
  }

  if (['flowNodes', 'userTasks'].includes(groupBy?.type)) {
    return {by: 'label', order: 'asc'};
  }

  if (groupBy?.type.toLowerCase().includes('variable')) {
    // Descending for Date and Boolean
    // Ascending for Integer, Double, Long, Date
    const order = ['Date', 'Boolean'].includes(groupBy.value.type) ? 'desc' : 'asc';
    return {by: 'key', order};
  }

  return {by: 'key', order: 'desc'};
}
