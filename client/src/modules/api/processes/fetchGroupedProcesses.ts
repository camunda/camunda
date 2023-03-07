/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {requestAndParse} from 'modules/request';

type ProcessVersionDto = {
  bpmnProcessId: string;
  id: string;
  name: string;
  version: number;
};

type ProcessDto = {
  bpmnProcessId: string;
  name: string | null;
  processes: ProcessVersionDto[];
  permissions?: PermissionDto[] | null;
};

const fetchGroupedProcesses = async () => {
  return requestAndParse<ProcessDto[]>({
    url: '/api/processes/grouped',
  });
};

export {fetchGroupedProcesses};
export type {ProcessDto, ProcessVersionDto};
