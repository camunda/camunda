/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';

export const buildMetadata = (
  metadata: MetaDataDto['instanceMetadata'] | null,
  incident: {
    errorType: {id: string; name: string};
    errorMessage: string;
  } | null,
) => {
  if (metadata === null) {
    return '';
  }

  const {
    flowNodeInstanceId,
    calledProcessInstanceId,
    calledDecisionInstanceId,
    ...metadataSubset
  } = metadata;

  return JSON.stringify({
    ...metadataSubset,
    incidentErrorType: incident?.errorType.name || null,
    incidentErrorMessage: incident?.errorMessage || null,
    flowNodeInstanceKey: flowNodeInstanceId,
    calledProcessInstanceKey: calledProcessInstanceId,
    calledDecisionInstanceKey: calledDecisionInstanceId,
  });
};
