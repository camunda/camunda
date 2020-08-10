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

  if (type === 'groupBy') {
    const distributedBy = props.report.data.configuration?.distributedBy;
    if (
      (data.type === 'userTasks' && distributedBy === 'userTask') ||
      (['assignee', 'candidateGroup'].includes(data.type) &&
        ['assignee', 'candidateGroup'].includes(distributedBy))
    ) {
      changes.configuration.distributedBy = {$set: 'none'};
    }
  }

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
    } else if (data.entity !== 'userTask' && props.report.data.view?.entity === 'userTask') {
      changes.configuration.distributedBy = {$set: 'none'};
    }
  }

  return changes;
};

export default config;
