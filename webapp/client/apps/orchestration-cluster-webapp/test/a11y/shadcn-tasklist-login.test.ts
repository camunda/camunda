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

test('should have no accessibility violations on theTasklist login page', async ({
	network,
	shadcnTasklistLoginPage,
	makeAxeBuilder,
}) => {
	network.use(mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}));

	await shadcnTasklistLoginPage.goto();
	await expect(shadcnTasklistLoginPage.submitButton).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations when showing aTasklist login error', async ({
	network,
	shadcnTasklistLoginPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
		mockLoginEndpoint({successResponse: new HttpResponse(null, {status: 401})}),
	);

	await shadcnTasklistLoginPage.goto();
	await shadcnTasklistLoginPage.fillCredentials('demo', 'wrong-password');
	await shadcnTasklistLoginPage.submitButton.click();
	await expect(shadcnTasklistLoginPage.errorMessage).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});
