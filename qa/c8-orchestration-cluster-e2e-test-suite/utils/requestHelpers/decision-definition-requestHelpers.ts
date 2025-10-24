/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Serializable} from 'playwright-core/types/structs';
import {expect} from '@playwright/test';
import {assertEqualsForKeys, assertRequiredFields} from '../http';
import {decisionDefinitionRequiredFields} from '../beans/requestBeans';
import {deploy} from '../zeebeClient';

export async function deployDecisionAndStoreResponse(
  state: Record<string, unknown>,
  key: string,
  file: string,
) {
  const response = await deploy([file]);
  expect(response.decisions.length).toBe(1);
  state[`decisionDefinition${key}`] = response.decisions[0];
}

export function assertDecisionDefinitionInResponse(
  json: Serializable,
  expectedBody: Serializable,
  decisionDefinitionKey: string,
) {
  const matchingItem = json.items.find(
    (it: {decisionDefinitionKey: string}) =>
      it.decisionDefinitionKey === decisionDefinitionKey,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, decisionDefinitionRequiredFields);
  assertEqualsForKeys(
    matchingItem,
    expectedBody,
    decisionDefinitionRequiredFields,
  );
}
