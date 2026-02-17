/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {sleep} from 'utils/sleep';

/**
 * Gets the locator for a process variable's value cell
 *
 * @param page - Playwright Page object
 * @param variableName - Name of the process variable
 * @returns Promise<Locator> - Locator for the variable value cell
 */
export async function getProcessVariableValue(
  page: Page,
  variableName: string,
): Promise<Locator> {
  return await page
    .getByTestId(`variable-${variableName}`)
    .getByRole('cell')
    .nth(1);
}

/**
 * Asserts that a process variable contains specific text
 * Includes retry logic with page reloads for flaky assertions
 *
 * @param page - Playwright Page object
 * @param variableName - Name of the process variable
 * @param text - Text that should be contained in the variable value
 * @throws Error if assertion fails after max retries
 */
export async function assertProcessVariableContainsText(
  page: Page,
  variableName: string,
  text: string,
): Promise<void> {
  const maxRetries = 3;
  for (let retries = 0; retries < maxRetries; retries++) {
    try {
      const valueCell = await getProcessVariableValue(page, variableName);
      await expect(valueCell).toContainText(text, {timeout: 30000});
      return;
    } catch (error) {
      console.log(`Failed to assert variable ${variableName}` + error);
      await page.reload();
      await sleep(10000);
    }
  }
  throw new Error(
    `Failed to assert variable ${variableName} contains ${text} after ${maxRetries} attempts.`,
  );
}
