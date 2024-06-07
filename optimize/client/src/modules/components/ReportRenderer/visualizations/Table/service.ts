/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {post} from 'request';

export async function loadObjectValues(
  name: string,
  processInstanceId: string,
  processDefinitionKey: string,
  processDefinitionVersions: string[],
  tenantIds: (string | null)[]
): Promise<string> {
  const response = await post(`api/variables/values`, {
    name,
    processInstanceId,
    processDefinitionKey,
    processDefinitionVersions,
    tenantIds,
    type: 'object',
  });

  const values = await response.json();
  return values[0];
}
