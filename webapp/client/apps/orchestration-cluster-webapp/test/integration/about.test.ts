/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';

import {mockCurrentUserEndpoint, mockAboutEndpoint} from '#/shared-test-modules/mock-handlers';

const ABOUT_MESSAGE = 'About page loaded from MSW';

test('should render mocked about data', async ({network, page}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json({}),
		}),
		mockAboutEndpoint({
			successResponse: HttpResponse.json({message: ABOUT_MESSAGE}),
		}),
	);

	await page.goto('/about');

	await expect(page.getByRole('heading', {name: 'About'})).toBeVisible();
	await expect(page.getByText(ABOUT_MESSAGE)).toBeVisible();
});

test('should render an error when about data fails to load', async ({network, page}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json({}),
		}),
		mockAboutEndpoint({
			successResponse: HttpResponse.json({error: 'Internal Server Error'}, {status: 500}),
		}),
	);

	await page.goto('/about');

	await expect(page.getByRole('heading', {name: 'About'})).toBeVisible();
	await expect(page.getByText('Unable to load about data')).toBeVisible();
});
