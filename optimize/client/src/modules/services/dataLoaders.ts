/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessFilter, Variable} from 'types';
import {get, post} from 'request';

export async function loadProcessDefinitionXml(
  key?: string,
  version?: string,
  tenantId?: string | null
): Promise<string | null> {
  const payload: {key?: string; version?: string; tenantId?: string} = {key, version};
  if (tenantId) {
    payload.tenantId = tenantId;
  }

  try {
    const response = await get('api/definition/process/xml', payload);

    return await response.text();
  } catch (_e) {
    return null;
  }
}

type Process = {
  processDefinitionKey: string;
  processDefinitionVersions: string[];
  tenantIds: (string | null)[];
};

type LoadVariablesPayload = {
  processesToQuery: Process[];
  filter: ProcessFilter[];
};

export const loadVariables = async (payload: LoadVariablesPayload) => {
  const response = await post('api/variables', payload);

  return (await response.json()) as Variable[];
};
