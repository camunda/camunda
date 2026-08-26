/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {test, expect} from '#/pw-modules/test-extend';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createQueryProcessDefinitionsResponse} from '#/shared-test-modules/api-mocks/process-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	mockCurrentUserEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';

test('should have no accessibility violations in the first-time process warning', async ({
	network,
	shadcnTasklistProcessesPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
		}),
	);

	await shadcnTasklistProcessesPage.goto();
	await expect(shadcnTasklistProcessesPage.firstTimeWarningDialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});
