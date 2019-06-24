/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'request';

export async function loadVariables(processDefinitionKey, processDefinitionVersion) {
  const response = await get('api/variables', {
    processDefinitionKey,
    processDefinitionVersion,
    namePrefix: '',
    sortOrder: 'asc',
    orderBy: 'name'
  });

  return await response.json();
}

export function isDurationHeatmap({
  view,
  visualization,
  processDefinitionKey,
  processDefinitionVersion
}) {
  return (
    view &&
    (view.entity === 'flowNode' || view.entity === 'userTask') &&
    view.property === 'duration' &&
    visualization === 'heat' &&
    processDefinitionKey &&
    processDefinitionVersion
  );
}

export function isProcessInstanceDuration({view}) {
  return view && view.entity === 'processInstance' && view.property === 'duration';
}
