/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get} from 'request';

export async function loadProcessDefinitionXml(
  key: string,
  version?: string,
  tenantId?: string | null
): Promise<string | null> {
  const payload: {key: string; version?: string; tenantId?: string} = {key, version};
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
