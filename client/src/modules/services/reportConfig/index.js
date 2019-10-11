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
  decision: reportConfig(decisionOptions)
};

const processUpdate = config.process.update;
config.process.update = (type, data, props) => {
  const changes = processUpdate(type, data, props);

  changes.configuration = {sorting: {$set: null}};

  if (type === 'view') {
    changes.configuration.heatmapTargetValue = {$set: {active: false, values: {}}};

    if (data.property !== 'duration' || data.entity !== 'processInstance') {
      changes.configuration.processPart = {$set: null};
    }

    if (
      data.entity === 'userTask' &&
      props.report.data.view &&
      props.report.data.view.entity !== 'userTask'
    ) {
      changes.configuration.hiddenNodes = {$set: {active: false, keys: []}};
    }
  }

  return changes;
};

export default config;
