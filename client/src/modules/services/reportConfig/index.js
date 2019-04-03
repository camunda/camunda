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

  changes.parameters = {sorting: {$set: null}};

  if (type === 'view') {
    changes.configuration = {heatmapTargetValue: {$set: {active: false, values: {}}}};

    if (data.property !== 'duration' || data.entity !== 'processInstance') {
      changes.parameters.processPart = {$set: null};
    }
  }

  return changes;
};

export default config;
