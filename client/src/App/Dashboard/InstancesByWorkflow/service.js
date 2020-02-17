/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import pluralSuffix from 'modules/utils/pluralSuffix';

export function concatUrl({
  bpmnProcessId,
  versions,
  hasFinishedInstances,
  name
}) {
  const versionId = versions.length === 1 ? versions[0].version : 'all';

  const filter = {
    workflow: bpmnProcessId,
    version: versionId.toString(),
    incidents: true,
    active: true
  };

  if (hasFinishedInstances) {
    Object.assign(filter, {
      completed: true,
      canceled: true
    });
  }

  return `/instances?filter=${JSON.stringify(filter)}&name=${JSON.stringify(
    name
  )}`;
}

export function concatGroupTitle(workflowName, instancesCount, versionsCount) {
  return `View ${pluralSuffix(instancesCount, 'Instance')} in ${pluralSuffix(
    versionsCount,
    'Version'
  )} of Workflow ${workflowName}`;
}

export function concatTitle(workflowName, instancesCount, versionName) {
  return `View ${pluralSuffix(
    instancesCount,
    'Instance'
  )} in Version ${versionName} of Workflow ${workflowName}`;
}

export function concatGroupLabel(name, instancesCount, versionsCount) {
  return `${name} – ${pluralSuffix(
    instancesCount,
    'Instance'
  )} in ${pluralSuffix(versionsCount, 'Version')}`;
}

export function concatLabel(name, instancesCount, version) {
  return `${name} – ${pluralSuffix(
    instancesCount,
    'Instance'
  )} in Version ${version}`;
}

export function concatButtonTitle(name, instancesCount) {
  return `Expand ${pluralSuffix(
    instancesCount,
    'Instance'
  )} of Workflow ${name}`;
}
