/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';

type ProcessDto = {
  id: string;
  name: string | null;
  version: number;
  bpmnProcessId: string;
  versionTag: string | null;
};

const fetchProcess = async (processId: string) => {
  return requestAndParse<ProcessDto>({
    url: `/api/processes/${processId}`,
    method: 'GET',
  });
};

export {fetchProcess};
export type {ProcessDto};
