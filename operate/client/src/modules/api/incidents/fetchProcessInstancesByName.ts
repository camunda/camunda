/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';

type ProcessDto = {
  processId: string;
  version: number;
  name: null | string;
  bpmnProcessId: string;
  tenantId: string;
  errorMessage: null | string;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
};
type ProcessInstanceByNameDto = {
  bpmnProcessId: string;
  tenantId: string;
  processName: null | string;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
  processes: ProcessDto[];
};

const fetchProcessInstancesByName = async (
  options?: Parameters<typeof requestAndParse>[1],
) => {
  return requestAndParse<ProcessInstanceByNameDto[]>(
    {
      url: '/api/incidents/byProcess',
    },
    options,
  );
};

export {fetchProcessInstancesByName};
export type {ProcessInstanceByNameDto};
