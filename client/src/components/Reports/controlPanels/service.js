/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'request';

const loadVariablesFrom = endpoint => async payload => {
  const response = await post(endpoint, payload);

  return await response.json();
};

export const loadVariables = loadVariablesFrom('api/variables');
export const loadInputVariables = loadVariablesFrom('api/decision-variables/inputs/names');
export const loadOutputVariables = loadVariablesFrom('api/decision-variables/outputs/names');

export function isDurationHeatmap({
  view,
  visualization,
  processDefinitionKey,
  processDefinitionVersions
}) {
  return (
    view &&
    (view.entity === 'flowNode' || view.entity === 'userTask') &&
    view.property === 'duration' &&
    visualization === 'heat' &&
    processDefinitionKey &&
    processDefinitionVersions &&
    processDefinitionVersions.length > 0
  );
}

export function isProcessInstanceDuration({view}) {
  return view && view.entity === 'processInstance' && view.property === 'duration';
}
