/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import percySnapshot from '@percy/playwright';
import type {Page} from '@playwright/test';
import {expect} from '@playwright/test';

const isPercyEnabled = Boolean(process.env.PERCY_TOKEN);

export async function takePercySnapshot(page: Page, name: string) {
  if (isPercyEnabled) {
    await percySnapshot(page, name);
  }
}

export async function visualSnapshot(page: Page, name: string) {
  if (isPercyEnabled) {
    await percySnapshot(page, name);
  } else {
    await expect(page).toHaveScreenshot();
  }
}
