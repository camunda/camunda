/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';
import type {ResourceBasedPermissionDto} from 'modules/types/operate';

type ProcessVersionDto = {
  bpmnProcessId: string;
  id: string;
  name: string;
  version: number;
  versionTag: string | null;
};

type ProcessDto = {
  bpmnProcessId: string;
  name: string | null;
  processes: ProcessVersionDto[];
  permissions?: ResourceBasedPermissionDto[] | null;
  tenantId: string;
};

const fetchGroupedProcesses = async (tenantId?: string) => {
  return requestAndParse<ProcessDto[]>({
    url: '/api/processes/grouped',
    method: 'POST',
    body: {tenantId},
  });
};

export {fetchGroupedProcesses};
export type {ProcessDto, ProcessVersionDto};
