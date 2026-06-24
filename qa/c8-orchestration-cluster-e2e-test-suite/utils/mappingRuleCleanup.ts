/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {APIRequestContext} from '@playwright/test';
import {jsonHeaders, buildUrl} from './http';

export async function cleanupMappingRules(
  request: APIRequestContext,
  mappingRuleIds: string[],
): Promise<void> {
  if (mappingRuleIds.length === 0) return;
  console.log(`Cleaning up ${mappingRuleIds.length} mapping rules via API...`);

  await Promise.allSettled(
    mappingRuleIds.map(async (mappingRuleId) => {
      try {
        const response = await request.delete(
          buildUrl('/mapping-rules/{mappingRuleId}', {mappingRuleId}),
          {headers: jsonHeaders()},
        );
        if (response.status() === 204) {
          console.log(`Successfully deleted mapping rule: ${mappingRuleId}`);
        } else if (response.status() === 404) {
          console.log(
            `Mapping rule already deleted or doesn't exist: ${mappingRuleId}`,
          );
        } else {
          console.warn(
            `Unexpected response status ${response.status()} for mapping rule ${mappingRuleId}`,
          );
        }
      } catch (error) {
        console.error(`Failed to delete mapping rule ${mappingRuleId}:`, error);
      }
    }),
  );
}
