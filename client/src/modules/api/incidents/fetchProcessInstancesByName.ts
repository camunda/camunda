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
  errorMessage: null | string;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
};
type ProcessInstanceByNameDto = {
  bpmnProcessId: string;
  processName: null | string;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
  processes: ProcessDto[];
};

const fetchProcessInstancesByName = async () => {
  return requestAndParse<ProcessInstanceByNameDto[]>({
    url: '/api/incidents/byProcess',
  });
};

export {fetchProcessInstancesByName};
export type {ProcessInstanceByNameDto};
