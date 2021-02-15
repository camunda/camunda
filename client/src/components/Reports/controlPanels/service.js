/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function isDurationHeatmap({
  view,
  visualization,
  processDefinitionKey,
  processDefinitionVersions,
}) {
  return (
    view &&
    (view.entity === 'flowNode' || view.entity === 'userTask') &&
    view.properties[0] === 'duration' &&
    visualization === 'heat' &&
    processDefinitionKey &&
    processDefinitionVersions &&
    processDefinitionVersions.length > 0
  );
}

export function isProcessInstanceDuration({view}) {
  return view && view.entity === 'processInstance' && view.properties[0] === 'duration';
}
