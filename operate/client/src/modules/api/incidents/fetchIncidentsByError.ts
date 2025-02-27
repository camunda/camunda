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
  tenantId: string;
  version: number;
  name: null | string;
  bpmnProcessId: string;
  errorMessage: string;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
};

type IncidentByErrorDto = {
  errorMessage: string;
  incidentErrorHashCode: number;
  instancesWithErrorCount: number;
  processes: ProcessDto[];
};

const fetchIncidentsByError = async (
  options?: Parameters<typeof requestAndParse>[1],
) => {
  return requestAndParse<IncidentByErrorDto[]>(
    {
      url: '/api/incidents/byError',
    },
    options,
  );
};

export {fetchIncidentsByError};
export type {ProcessDto, IncidentByErrorDto};
