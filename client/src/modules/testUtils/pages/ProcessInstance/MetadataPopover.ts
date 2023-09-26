/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

export class MetaDataPopover {
  readonly labels = {
    // headings
    details: /^details$/i,
    incidents: /^incidents$/i,
    incident: /^incident$/i,
    // view buttons
    showMoreMetadata: /^show more metadata$/i,
    showIncidents: /^show incidents$/i,
    showIncident: /^show incident$/i,
    // details
    flowNodeInstanceKey: /^flow node instance key$/i,
    executionDuration: /^execution duration$/i,
    calledProcessInstance: /^called process instance$/i,
    calledDecisionInstance: /^called decision instance$/i,
    retriesLeft: /^retries left$/i,
    // incidents
    type: /^type$/i,
    errorMessage: /^error message$/i,
    rootCauseProcessInstance: /^root cause process instance$/i,
    rootCauseDecisionInstance: /^root cause decision instance$/i,
  } as const;
}
