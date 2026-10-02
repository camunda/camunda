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
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createQueryProcessDefinitionsResponse} from '#/shared-test-modules/api-mocks/process-definitions';
import {createQueryProcessInstancesResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {
	mockCurrentUserEndpoint,
	mockSystemConfigurationEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
} from '#/shared-test-modules/mock-handlers';

// Carbon's invalid TextInput sets `aria-errormessage` without announcing the message through
// `aria-describedby` or a live region, so axe flags every invalid Carbon field. There is no prop
// to change that, so it cannot be fixed here — remove the exclusion once Carbon announces it.
// Every other rule, and every other element, is still scanned.
const CARBON_INVALID_INPUT_RULE = 'aria-valid-attr-value';

test('should have no accessibility violations in variable filters and their condition modal', async ({
	network,
	page,
	operateProcessesPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockGetProcessDefinitionInstanceStatisticsEndpoint({successResponse: HttpResponse.json(createPaginatedResponse())}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockQueryProcessDefinitionsEndpoint({successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse())}),
		mockQueryProcessInstancesEndpoint({successResponse: HttpResponse.json(createQueryProcessInstancesResponse())}),
	);
	await operateProcessesPage.goto();
	await operateProcessesPage.addOptionalFilter('Variables');
	await page.getByRole('textbox', {name: 'Name'}).fill('status');
	await expect(page.getByText('Value has to be filled')).toBeVisible();
	expect((await makeAxeBuilder().disableRules([CARBON_INVALID_INPUT_RULE]).analyze()).violations).toEqual([]);

	await page.getByRole('button', {name: 'Add condition'}).click();
	await expect(operateProcessesPage.variableFilterModal).toBeVisible();
	await operateProcessesPage.variableFilterModal.getByRole('button', {name: 'Apply'}).click();
	await expect(operateProcessesPage.variableFilterModal.getByText('Value is required')).toBeVisible();
	expect((await makeAxeBuilder().disableRules([CARBON_INVALID_INPUT_RULE]).analyze()).violations).toEqual([]);
});
