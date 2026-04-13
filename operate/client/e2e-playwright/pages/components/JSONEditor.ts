/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator, Page} from '@playwright/test';

class JSONEditor {
  private readonly page: Page;
  private readonly codeEditor: Locator;

  constructor(page: Page) {
    this.page = page;
    this.codeEditor = this.page.getByRole('code').first();
  }

  getEditor(label: string) {
    return this.page.getByRole('group', {name: label});
  }

  async waitForEditorToLoad() {
    await this.codeEditor.waitFor({state: 'visible'});
    await this.page
      .locator('.monaco-editor .cursor')
      .first()
      .waitFor({state: 'visible'});
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

export {JSONEditor};
