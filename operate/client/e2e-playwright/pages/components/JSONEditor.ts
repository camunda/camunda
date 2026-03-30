/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator, Page} from '@playwright/test';

export class JSONEditor {
  private readonly page: Page;
  readonly editor: Locator;

  constructor(page: Page) {
    this.page = page;
    this.editor = page.getByRole('code').nth(0);
  }

  async waitForEditor() {
    await this.editor.locator('textarea').waitFor({state: 'attached'});
  }

  async select() {
    await this.editor.click();
  }

  async fill(value: string) {
    await this.page.keyboard.insertText(value);
  }

  async clear() {
    await this.page.keyboard.press('Control+A');
    await this.page.keyboard.press('Backspace');
  }

  async blur() {
    await this.page.keyboard.press('Escape');
  }
}
