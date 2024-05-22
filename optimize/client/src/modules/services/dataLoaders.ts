/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
  } catch (e) {
    return null;
  }
}

export async function loadDecisionDefinitionXml(
  key: string,
  version?: string,
  tenantId?: string | null
): Promise<string | null> {
  const payload: {key: string; version?: string; tenantId?: string} = {key, version};
  if (tenantId) {
    payload.tenantId = tenantId;
  }
  try {
    const response = await get('api/definition/decision/xml', payload);

    return await response.text();
  } catch (e) {
    return null;
  }
}

const loadVariablesFrom =
  <P>(endpoint: string) =>
  async (payload: P) => {
    const response = await post(endpoint, payload);

    return (await response.json()) as Variable[];
  };

type Process = {
  processDefinitionKey: string;
  processDefinitionVersions: string[];
  tenantIds: (string | null)[];
};

type LoadVariablesPayload = {
  processesToQuery: Process[];
  filter: ProcessFilter[];
};
export const loadVariables = loadVariablesFrom<LoadVariablesPayload>('api/variables');

type LoadDecisionVariablesPayload = {
  decisionDefinitionKey: string;
  decisionDefinitionVersions: string[];
  tenantIds: (string | null)[];
};
export const loadInputVariables = loadVariablesFrom<LoadDecisionVariablesPayload>(
  'api/decision-variables/inputs/names'
);
export const loadOutputVariables = loadVariablesFrom<LoadDecisionVariablesPayload>(
  'api/decision-variables/outputs/names'
);
