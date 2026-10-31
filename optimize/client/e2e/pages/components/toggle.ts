/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type Locator} from '@playwright/test';

// Carbon toggles overlay their switch with the label, so the label receives the click.
export async function setToggle(toggle: Locator, checked: boolean): Promise<void> {
  const state = String(checked);
  if ((await toggle.getAttribute('aria-checked')) !== state) {
    const labelId = await toggle.getAttribute('aria-labelledby');
    await toggle.page().locator(`[id="${labelId}"]`).click();
  }
  await expect(toggle).toHaveAttribute('aria-checked', state);
}

// Carbon checkboxes hide the native input behind their label.
export async function setCheckbox(checkbox: Locator, checked: boolean): Promise<void> {
  if ((await checkbox.isChecked()) !== checked) {
    const id = await checkbox.getAttribute('id');
    await checkbox.page().locator(`label[for="${id}"]`).click();
  }
  await (checked ? expect(checkbox).toBeChecked() : expect(checkbox).not.toBeChecked());
}
