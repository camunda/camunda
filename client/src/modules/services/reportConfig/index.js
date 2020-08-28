/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

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
  changes.configuration.sorting = {$set: null};

  if (type === 'view') {
    changes.configuration.heatmapTargetValue = {$set: {active: false, values: {}}};

    if (data.entity !== 'variable' && props.report.data.configuration?.aggregationType === 'sum') {
      changes.configuration.aggregationType = {$set: 'avg'};
    }

    if (data.property !== 'duration' || data.entity !== 'processInstance') {
      changes.configuration.processPart = {$set: null};
    }

    if (data.entity === 'userTask' && props.report.data.view?.entity !== 'userTask') {
      changes.configuration.hiddenNodes = {$set: {active: false, keys: []}};
    }
  }

  if (shouldResetDistributedBy(type, data, props.report.data)) {
    changes.configuration.distributedBy = {$set: {type: 'none', value: null}};
  }

  return changes;
};

function shouldResetDistributedBy(type, data, report) {
  if (report.view?.entity === 'flowNode') {
    // flow node reports: reset when changing from date for flow node grouping
    if (type === 'groupBy' && data.type === 'flowNodes') {
      return true;
    }

    // flow node reports: reset when changing view to anything else
    if (type === 'view' && data.entity !== 'flowNode') {
      return true;
    }
  }

  if (report.view?.entity === 'userTask') {
    // user task report: reset when it's distributed by usertask and we switch to group by usertask
    if (
      type === 'groupBy' &&
      data.type === 'userTasks' &&
      report.configuration?.distributedBy.type === 'userTask'
    ) {
      return true;
    }

    // user task report: reset when it's distributed by assignee and we switch to group by assignee
    if (
      type === 'groupBy' &&
      ['assignee', 'candidateGroup'].includes(data.type) &&
      ['assignee', 'candidateGroup'].includes(report.configuration?.distributedBy.type)
    ) {
      return true;
    }

    // user task report: reset when changing view to anything else
    if (type === 'view' && data.entity !== 'userTask') {
      return true;
    }
  }

  return false;
}

export default config;
