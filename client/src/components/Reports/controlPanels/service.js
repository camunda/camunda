/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'request';
import {getDataKeys} from 'services';

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

export function isChecked(data, current) {
  return (
    current &&
    getDataKeys(data).every(
      prop =>
        JSON.stringify(current[prop]) === JSON.stringify(data[prop]) || Array.isArray(data[prop])
    )
  );
}

export function isDurationHeatmap({
  view,
  visualization,
  processDefinitionKey,
  processDefinitionVersion
}) {
  return (
    view &&
    ((view.entity === 'flowNode' && view.property === 'duration') || view.entity === 'userTask') &&
    visualization === 'heat' &&
    processDefinitionKey &&
    processDefinitionVersion
  );
}

export function isProcessInstanceDuration({view}) {
  return view && view.entity === 'processInstance' && view.property === 'duration';
}
