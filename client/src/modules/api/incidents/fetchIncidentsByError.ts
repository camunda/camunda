/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

type ProcessDto = {
  processId: string;
  version: number;
  name: null | string;
  bpmnProcessId: string;
  errorMessage: string;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
};

type IncidentByErrorDto = {
  errorMessage: string;
  instancesWithErrorCount: number;
  processes: ProcessDto[];
};

const fetchIncidentsByError = async () => {
  return requestAndParse<IncidentByErrorDto[]>({
    url: '/api/incidents/byError',
  });
};

export {fetchIncidentsByError};
export type {ProcessDto, IncidentByErrorDto};
