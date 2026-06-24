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

export function assertClientsInResponse(json: Serializable, client: string) {
  const matchingItem = json.items.find(
    (it: {clientId: string}) => it.clientId === client,
  );
  expect(matchingItem).toBeDefined();
  assertRequiredFields(matchingItem, ['clientId']);
  assertEqualsForKeys(matchingItem, {clientId: client}, ['clientId']);
}
