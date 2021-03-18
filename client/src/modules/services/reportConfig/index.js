/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import update from 'immutability-helper';

import {default as reportConfig} from './reportConfig';
import * as decisionOptions from './decision';
import * as processOptions from './process';

const config = {
  process: reportConfig(processOptions),
  decision: reportConfig(decisionOptions),
};

const processUpdate = config.process.update;
config.process.update = (type, data, props) => {
  const changes = processUpdate(type, data, props);

  changes.configuration = changes.configuration || {};

  if (type === 'view') {
    changes.configuration.heatmapTargetValue = {$set: {active: false, values: {}}};

    if (
      data.entity !== 'variable' &&
      props.report.data.configuration?.aggregationTypes?.includes('sum')
    ) {
      changes.configuration.aggregationType = {$set: 'avg'};
      changes.configuration.aggregationTypes = {$set: ['avg']};
    }

    if (data.properties[0] !== 'duration' || data.entity !== 'processInstance') {
      changes.configuration.processPart = {$set: null};
    }

    if (data.entity === 'userTask' && props.report.data.view?.entity !== 'userTask') {
      changes.configuration.hiddenNodes = {$set: {active: false, keys: []}};
    }

    if (data.properties.length > 1) {
      // multi-measure reports do not support goals
      changes.configuration.targetValue = {active: {$set: false}};
    }
  }

  if (shouldResetDistributedBy(type, data, props.report.data)) {
    changes.distributedBy = {$set: {type: 'none', value: null}};
  }

  const newReport = update(props.report, {data: changes});
  changes.configuration.sorting = {$set: getDefaultSorting(newReport)};
  changes.configuration.tableColumns = {
    ...changes.configuration.tableColumns,
    columnOrder: {$set: []},
  };

  // automatically distribute by flownode/usertasks when view is flownode/usertask
  if (
    newReport.data.distributedBy?.type === 'none' &&
    newReport.data.view?.properties.length <= 1 &&
    isFlowNodeViewNonFlowNodeGroupBy(newReport) &&
    // do not automatically set the distributed by if it was explicitely set to none
    !(
      isFlowNodeViewNonFlowNodeGroupBy(props.report) &&
      props.report.data.distributedBy?.type === 'none'
    )
  ) {
    changes.distributedBy = {$set: {type: newReport.data.view.entity, value: null}};

    if (!['line', 'table'].includes(props.report.data?.visualization)) {
      changes.visualization = {$set: 'bar'};
    }
  }

  return changes;
};

const decisionUpdate = config.decision.update;
config.decision.update = (type, data, props) => {
  const changes = decisionUpdate(type, data, props);
  changes.configuration = changes.configuration || {};
  changes.configuration.sorting = {$set: getDefaultSorting(update(props.report, {data: changes}))};
  changes.configuration.tableColumns = {
    ...changes.configuration.tableColumns,
    columnOrder: {$set: []},
  };

  return changes;
};

function shouldResetDistributedBy(type, data, report) {
  if (report.view?.entity === 'flowNode') {
    // flow node reports: reset when changing from date for flow node grouping
    if (type === 'groupBy' && data.type === 'flowNodes') {
      return true;
    }

    if (type === 'view') {
      // flow node reports: reset when changing view to anything else
      if (data.entity !== 'flowNode') {
        return true;
      }

      // flow node reports: reset when changing from count to duration view when grouped by duration
      if (data.properties[0] === 'duration' && report.groupBy.type === 'duration') {
        return true;
      }
    }
  }

  if (report.view?.entity === 'userTask') {
    // user task report: reset when it's distributed by usertask and we switch to group by usertask
    if (
      type === 'groupBy' &&
      data.type === 'userTasks' &&
      report.distributedBy.type === 'userTask'
    ) {
      return true;
    }

    // user task report: reset when it's distributed by assignee and we switch to group by assignee/duration
    if (
      type === 'groupBy' &&
      ['assignee', 'candidateGroup', 'duration'].includes(data.type) &&
      ['assignee', 'candidateGroup'].includes(report.distributedBy.type)
    ) {
      return true;
    }

    if (type === 'view') {
      // user task report: reset when changing view to anything else
      if (data.entity !== 'userTask') {
        return true;
      }

      // user task report: reset when changing from count to duration view when grouped by duration
      if (data.properties[0] === 'duration' && report.groupBy.type === 'duration') {
        return true;
      }
    }
  }

  if (report.view?.entity === 'processInstance') {
    // process instance reports: reset when it's distributed by variable and we switch from start/end date to any other grouping
    if (type === 'groupBy') {
      if (
        report.distributedBy.type === 'variable' &&
        data.type !== 'startDate' &&
        data.type !== 'endDate'
      ) {
        return true;
      }

      // process instance reports: reset when it's distributed by start/end date and we switch from variable to any other grouping
      if (
        ['startDate', 'endDate'].includes(report.distributedBy.type) &&
        data.type !== 'variable'
      ) {
        return true;
      }
    }

    // process instance reports: reset when changing view to anything else
    if (type === 'view' && data.entity !== 'processInstance') {
      return true;
    }
  }

  return false;
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

function isFlowNodeViewNonFlowNodeGroupBy(report) {
  return (
    ['userTask', 'flowNode'].includes(report.data.view?.entity) &&
    !['userTasks', 'flowNodes'].includes(report.data.groupBy?.type)
  );
}

export default config;
