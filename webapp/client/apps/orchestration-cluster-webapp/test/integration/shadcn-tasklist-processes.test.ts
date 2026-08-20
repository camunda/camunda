/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {http, HttpResponse} from 'msw';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.10';
import {
	mockCurrentUserEndpoint,
	mockLicenseEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
	);
});

test('should display the page heading, description, and placeholder process tiles', async ({
	shadcnTasklistProcessesPage,
}) => {
	await shadcnTasklistProcessesPage.goto();

	await expect(shadcnTasklistProcessesPage.heading).toBeVisible();
	await expect(shadcnTasklistProcessesPage.description).toBeVisible();
	await expect(shadcnTasklistProcessesPage.startProcessButtons).toHaveCount(3);
	for (const button of await shadcnTasklistProcessesPage.startProcessButtons.all()) {
		await expect(button).toBeDisabled();
	}
});

test('should not fetch process definitions, since this layout scaffold has no data fetching wired in yet', async ({
	network,
	shadcnTasklistProcessesPage,
}) => {
	let processDefinitionsRequestCount = 0;
	network.use(
		http.post(endpoints.queryProcessDefinitions.getUrl(), () => {
			processDefinitionsRequestCount++;
			return HttpResponse.json({items: [], page: {totalItems: 0}});
		}),
	);

	await shadcnTasklistProcessesPage.goto();

	await expect(shadcnTasklistProcessesPage.heading).toBeVisible();
	expect(processDefinitionsRequestCount).toBe(0);
});
