/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {
	mockCurrentUserEndpoint,
	mockLicenseEndpoint,
	mockSystemConfigurationEndpoint,
	mockQueryClusterVariablesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createClusterVariableSearchResult,
	createQueryClusterVariablesResponse,
} from '#/shared-test-modules/api-mocks/cluster-variables';

// TEMPORARY: the design system's DataTable injects the row-actions column with
// `header: () => null`, so its `<th>` has no discernible text and axe flags every table
// that uses `rowActions`. There is no prop to name that column, so this cannot be fixed
// here — remove the exclusion once the design system names it. Every other rule, and
// every other element, is still scanned.
const DS_ACTIONS_COLUMN_HEADER_RULE = 'empty-table-header';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['admin']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryClusterVariablesEndpoint({
			successResponse: HttpResponse.json(
				createQueryClusterVariablesResponse([
					createClusterVariableSearchResult({name: 'api-base-url', value: '"https://api.example.com"'}),
				]),
			),
		}),
	);
});

test('should have no accessibility violations in the populated cluster variables page', async ({
	adminClusterVariablesPage,
	makeAxeBuilder,
}) => {
	await adminClusterVariablesPage.goto();
	await expect(adminClusterVariablesPage.row('api-base-url')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the empty state', async ({
	adminClusterVariablesPage,
	makeAxeBuilder,
	network,
	page,
}) => {
	network.use(
		mockQueryClusterVariablesEndpoint({successResponse: HttpResponse.json(createQueryClusterVariablesResponse())}),
	);

	await adminClusterVariablesPage.goto();
	await expect(page.getByText('No cluster variables found.')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations with the create cluster variable modal open', async ({
	adminClusterVariablesPage,
	makeAxeBuilder,
}) => {
	await adminClusterVariablesPage.goto();
	await adminClusterVariablesPage.addButton.click();
	await expect(adminClusterVariablesPage.addModal.valueEditor).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});
