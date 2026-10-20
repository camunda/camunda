/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {mockCurrentUserEndpoint, mockLoginEndpoint} from '#/shared-test-modules/mock-handlers';

test('should have no accessibility violations on the login page', async ({network, loginPage, makeAxeBuilder}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: new HttpResponse(null, {status: 401}),
		}),
	);

	await loginPage.goto();
	await expect(loginPage.submitButton).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations when showing a login error', async ({
	network,
	loginPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: new HttpResponse(null, {status: 401}),
		}),
		mockLoginEndpoint({
			successResponse: new HttpResponse(null, {status: 401}),
		}),
	);

	await loginPage.goto();
	await loginPage.fillCredentials('demo', 'wrong-password');
	await loginPage.submitButton.click();
	await expect(loginPage.errorMessage).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});
