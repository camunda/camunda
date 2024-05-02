/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';

type InstanceMetaDataDto = {
  flowNodeInstanceId: string;
  flowNodeId: string;
  flowNodeType: string;
  startDate: string;
  endDate: string | null;
  calledProcessInstanceId: string | null;
  calledProcessDefinitionName: string | null;
  calledDecisionInstanceId: string | null;
  calledDecisionDefinitionName: string | null;
  eventId: string;
  jobType: string | null;
  jobRetries: number | null;
  jobWorker: string | null;
  jobDeadline: string | null;
  jobCustomHeaders: {[key: string]: string} | null;
  jobId: string | null;
};

type MetaDataDto = {
  flowNodeInstanceId: string | null;
  flowNodeId: string | null;
  flowNodeType: string | null;
  instanceCount: number | null;
  instanceMetadata: InstanceMetaDataDto | null;
  incidentCount: number;
  incident: {
    id: string;
    errorType: {
      id: string;
      name: string;
    };
    errorMessage: string;
    flowNodeId: string;
    flowNodeInstanceId: string;
    jobId: string | null;
    creationTime: string;
    hasActiveOperation: boolean;
    lastOperation: boolean | null;
    rootCauseInstance: {
      instanceId: string;
      processDefinitionId: string;
      processDefinitionName: string;
    } | null;
    rootCauseDecision: {
      instanceId: string;
      decisionName: string;
    } | null;
  } | null;
};

async function fetchFlowNodeMetaData({
  processInstanceId,
  flowNodeId,
  flowNodeInstanceId,
  flowNodeType,
}: {
  processInstanceId: ProcessInstanceEntity['id'];
  flowNodeId: string;
  flowNodeInstanceId?: string;
  flowNodeType?: string;
}) {
  return requestAndParse<MetaDataDto>({
    url: `/api/process-instances/${processInstanceId}/flow-node-metadata`,
    method: 'POST',
    body:
      flowNodeInstanceId === undefined
        ? {flowNodeId, flowNodeInstanceId, flowNodeType}
        : {flowNodeInstanceId, flowNodeType},
  });
}

export {fetchFlowNodeMetaData};
export type {MetaDataDto};
