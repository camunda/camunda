/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
