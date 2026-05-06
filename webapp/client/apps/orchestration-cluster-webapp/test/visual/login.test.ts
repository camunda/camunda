/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';

test('should match the login page snapshot', async ({page}) => {
	await page.goto('/login');
	await expect(page.getByRole('button', {name: /login/i})).toBeVisible();

	await expect(page).toHaveScreenshot('login.png', {
		fullPage: true,
	});
});
