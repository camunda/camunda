/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';

export const buildMetadata = (
  metadata: MetaDataDto['instanceMetadata'] | null,
  incident: {errorType: {id: string; name: string}; errorMessage: string} | null
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
