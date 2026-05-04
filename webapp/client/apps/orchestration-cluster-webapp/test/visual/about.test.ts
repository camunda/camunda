/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';

import {createEndpointMock} from '#/shared-test-modules/mock-endpoint';

const ABOUT_MESSAGE = 'About page loaded from MSW';

const mockAboutEndpoint = createEndpointMock({
	endpoint: '/api/about',
	method: 'GET',
});

test('should match the about page snapshot', async ({network, page}) => {
	network.use(
		mockAboutEndpoint({
			successResponse: HttpResponse.json({message: ABOUT_MESSAGE}),
		}),
	);

	await page.goto('/about');
	await expect(page.getByText(ABOUT_MESSAGE)).toBeVisible();

	await expect(page).toHaveScreenshot('about.png', {
		fullPage: true,
	});
});
