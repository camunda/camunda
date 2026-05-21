/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, Locator} from '@playwright/test';

export async function assertJsonEqual(selector: Locator, expected: object) {
  const text = await selector.innerText();
  expect(JSON.parse(text)).toEqual(expected);
}
