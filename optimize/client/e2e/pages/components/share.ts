/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, type Page} from '@playwright/test';

import {setToggle} from './toggle';

// Enables public sharing of the currently viewed report or dashboard and returns the share link.
export async function enableSharing(page: Page): Promise<string> {
  await page.getByRole('main').getByRole('button', {name: 'Share', exact: true}).click();
  await setToggle(page.getByRole('switch', {name: 'Enable sharing'}), true);
  const link = page.getByRole('textbox', {name: 'Link'});
  await expect(link).toHaveValue(/\/external\/#\/share\//);
  return link.inputValue();
}
