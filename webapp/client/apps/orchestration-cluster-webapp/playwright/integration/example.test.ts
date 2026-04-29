/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';

test('has title', async ({page}) => {
	await page.goto('https://playwright.dev/');

	// Expect a title "to contain" a substring.
	await expect(page).toHaveTitle(/Playwright/);
});

test('get started link', async ({page}) => {
	await page.goto('https://playwright.dev/');

	// Click the get started link.
	await page.getByRole('link', {name: 'Get started'}).click();

	// Expects page to have a heading with the name of Installation.
	await expect(page.getByRole('heading', {name: 'Installation'})).toBeVisible();
});
