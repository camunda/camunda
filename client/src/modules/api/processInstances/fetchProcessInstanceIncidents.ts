/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

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
  processInstanceId: ProcessInstanceEntity['id']
) => {
  return requestAndParse<ProcessInstanceIncidentsDto>({
    url: `/api/process-instances/${processInstanceId}/incidents`,
  });
};

export {fetchProcessInstanceIncidents};
export type {ProcessInstanceIncidentsDto, IncidentDto};
