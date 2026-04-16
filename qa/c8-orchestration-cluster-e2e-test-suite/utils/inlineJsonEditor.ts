/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, Locator, Page} from '@playwright/test';

export function getInlineJSONEditor(page: Page, row: Locator) {
  return {
    editReadModeValue: row.getByTestId('edit-variable-value-readonly'),
    readModeValue: row.getByTestId('json-editor-readonly-value'),
    writeModeValue: row.getByRole('code'),
    waitForEditorLoaded: async () => {
      await row
        .locator('.monaco-editor .cursor')
        .first()
        .waitFor({state: 'visible'});
    },
    fill: async (value: string) => {
      await page.keyboard.insertText(value);
    },
    clear: async () => {
      await page.keyboard.press('Control+A');
      await page.keyboard.press('Backspace');
    },
  };
}

export async function expectJsonEqual(selector: Locator, expected: object) {
  const text = await selector.innerText();
  expect(JSON.parse(text)).toEqual(expected);
}
