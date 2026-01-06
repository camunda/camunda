/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';
import type {ProcessInstanceEntity} from 'modules/types/operate';

type FlowNodeDto = {
  id: string;
  count: number;
};
type ErrorTypeDto = {
  id: string;
  name: string;
  count: number;
};
type IncidentDto = {
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
  lastOperation: null | unknown;
  rootCauseInstance: null | {
    instanceId: string;
    processDefinitionId: string;
    processDefinitionName: string;
  };
};

type ProcessInstanceIncidentsDto = {
  count: number;
  incidents: IncidentDto[];
  errorTypes: ErrorTypeDto[];
  flowNodes: FlowNodeDto[];
};

const fetchProcessInstanceIncidents = async (
  processInstanceId: ProcessInstanceEntity['id'],
  options?: Parameters<typeof requestAndParse>[1],
) => {
  return requestAndParse<ProcessInstanceIncidentsDto>(
    {
      url: `/api/process-instances/${processInstanceId}/incidents`,
    },
    options,
  );
};

export {fetchProcessInstanceIncidents};
export type {ProcessInstanceIncidentsDto, IncidentDto};
