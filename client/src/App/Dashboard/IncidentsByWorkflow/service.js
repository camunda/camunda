/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function getUrl(bpmnProcessId, version) {
  const urlVersion = version.toString().includes(',') ? 'all' : version;
  return `/instances?filter={"workflow":"${bpmnProcessId}","version":"${urlVersion}","incidents":true}`;
}

export function getTitle(workflowName, version, incidentsCount) {
  const isOneVersion = !version.toString().includes(',');
  return `View ${incidentsCount} Instances with Incidents in version${
    isOneVersion ? '' : 's'
  } ${version} of Workflow ${workflowName}`;
}
