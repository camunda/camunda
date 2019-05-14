/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import pluralSuffix from 'modules/utils/pluralSuffix';

export function getUrl(bpmnProcessId, version) {
  const urlVersion = version.toString().includes(',') ? 'all' : version;
  return `/instances?filter={"workflow":"${bpmnProcessId}","version":"${urlVersion}","incidents":true}`;
}

export function getGroupTitle(workflowName, instancesCount, versionsCount) {
  return `View ${pluralSuffix(instancesCount, 'Instance')} in ${pluralSuffix(
    versionsCount,
    'Version'
  )} of Workflow ${workflowName}`;
}

export function getTitle(workflowName, instancesCount, versionName) {
  return `View ${pluralSuffix(
    instancesCount,
    'Instance'
  )} in Version ${versionName} of Workflow ${workflowName}`;
}

export function getGroupLabel(name, instancesCount, versionsCount) {
  return `${name} – ${pluralSuffix(
    instancesCount,
    'Instance'
  )} in ${pluralSuffix(versionsCount, 'Version')}`;
}

export function getLabel(name, instancesCount, version) {
  return `${name} – ${pluralSuffix(
    instancesCount,
    'Instance'
  )} in Version ${version}`;
}

export function getButtonTitle(name, instancesCount) {
  return `Expand ${pluralSuffix(
    instancesCount,
    'Instance'
  )} of Workflow ${name}`;
}
