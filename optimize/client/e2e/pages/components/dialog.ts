/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type Page} from '@playwright/test';

// Carbon prefixes danger buttons with a visually hidden "danger", so match names loosely.
export async function confirmDialog(
  page: Page,
  title: string | RegExp,
  button: string | RegExp
): Promise<void> {
  const dialog = page.getByRole('dialog', {name: title});
  const name = typeof button === 'string' ? new RegExp(`${button}$`) : button;
  await dialog.getByRole('button', {name}).click();
  await expect(dialog).toBeHidden();
}
