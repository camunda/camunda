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
					createClusterVariableSearchResult({
						name: 'retry-limit',
						value: '3',
						scope: 'TENANT',
						tenantId: 'tenant-a',
					}),
				]),
			),
		}),
	);
});

test('should match the cluster variables page snapshot', async ({adminClusterVariablesPage, page}) => {
	await adminClusterVariablesPage.goto();
	await expect(adminClusterVariablesPage.row('api-base-url')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the create cluster variable modal snapshot', async ({adminClusterVariablesPage, network, page}) => {
	network.use(
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(
				createSystemConfiguration({
					components: {active: ['admin']},
					deployment: {isMultiTenancyEnabled: true, isTenantsApiEnabled: true, maxRequestSize: 0},
				}),
			),
		}),
	);
	await adminClusterVariablesPage.goto();
	await adminClusterVariablesPage.addButton.click();

	await expect(adminClusterVariablesPage.addModal.dialog).toBeVisible();
	await expect(adminClusterVariablesPage.addModal.valueEditor).toBeVisible();
	// Monaco paints a blinking cursor and, in its overview ruler and scrollbar, cursor-position marks
	// that appear depending on focus timing; Playwright's animation and caret handling covers none of it.
	await page.addStyleTag({
		content:
			'.monaco-editor .cursors-layer, .monaco-editor .scrollbar, .monaco-editor .decorationsOverviewRuler {visibility: hidden !important;}',
	});
	await expect(page).toHaveScreenshot();
});
