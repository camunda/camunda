/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
