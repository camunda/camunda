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
  private readonly readOnlyEditor: Locator;
  private readonly codeEditor: Locator;
  readonly editorWrapper: Locator;

  constructor(page: Page) {
    this.page = page;
    this.editorWrapper = page.getByTestId('json-editor-wrapper');
    this.readOnlyEditor = this.editorWrapper.getByTestId(
      'json-editor-readonly',
    );
    this.codeEditor = page.getByRole('code').first();
  }

  getFirstWrapper(): Locator {
    return this.editorWrapper.first();
  }

  getLastWrapper(): Locator {
    return this.editorWrapper.last();
  }

  async waitForEditor() {
    await this.codeEditor.waitFor({state: 'visible'});
  }

  async fill(value: string) {
    await this.page.keyboard.insertText(value);
  }

  async clear() {
    await this.page.keyboard.press('ControlOrMeta+A');
    await this.page.keyboard.press('Backspace');
  }

  async blur() {
    await this.page.keyboard.press('Escape');
  }
}
