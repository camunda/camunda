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
	mockSearchGlobalTaskListenersEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createGlobalTaskListener,
	createQueryGlobalTaskListenersResponse,
} from '#/shared-test-modules/api-mocks/global-task-listeners';

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
		mockSearchGlobalTaskListenersEndpoint({
			successResponse: HttpResponse.json(
				createQueryGlobalTaskListenersResponse({
					items: [createGlobalTaskListener({id: 'creation-notifier', type: 'notify-on-creation'})],
				}),
			),
		}),
	);
});

test('should have no accessibility violations in the populated global task listeners page', async ({
	adminGlobalTaskListenersPage,
	makeAxeBuilder,
}) => {
	await adminGlobalTaskListenersPage.goto();
	await expect(adminGlobalTaskListenersPage.row('creation-notifier')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the empty state', async ({
	adminGlobalTaskListenersPage,
	makeAxeBuilder,
	network,
	page,
}) => {
	network.use(
		mockSearchGlobalTaskListenersEndpoint({
			successResponse: HttpResponse.json(createQueryGlobalTaskListenersResponse({items: []})),
		}),
	);

	await adminGlobalTaskListenersPage.goto();
	await expect(page.getByText('No global task listeners found.')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().disableRules([DS_ACTIONS_COLUMN_HEADER_RULE]).analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations with the add global task listener modal open', async ({
	adminGlobalTaskListenersPage,
	makeAxeBuilder,
}) => {
	await adminGlobalTaskListenersPage.goto();
	await adminGlobalTaskListenersPage.addButton.click();
	await expect(adminGlobalTaskListenersPage.addModal.dialog).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();

	expect(accessibilityScanResults.violations).toEqual([]);
});
