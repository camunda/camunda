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
	mockQueryProcessInstancesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockQueryProcessInstancesEndpoint({successResponse: HttpResponse.json(createQueryProcessInstancesResponse())}),
	);
});

test('should have no accessibility violations in the cases table', async ({
	network,
	tasklistCasesPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [
						createProcessInstance({
							processInstanceKey: '2251799813685301',
							processDefinitionName: 'Home loan application',
							businessId: 'HL-28005',
						}),
						createProcessInstance({
							processInstanceKey: '2251799813685302',
							processDefinitionName: 'Insurance claim',
							businessId: 'IC-28011',
							hasIncident: true,
						}),
					],
				}),
			),
		}),
	);

	await tasklistCasesPage.goto();
	await expect(tasklistCasesPage.caseLink('Home loan application, HL-28005')).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the no open cases empty state', async ({
	tasklistCasesPage,
	makeAxeBuilder,
}) => {
	await tasklistCasesPage.goto();
	await expect(tasklistCasesPage.noOpenCasesHeading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations in the no matching cases empty state', async ({
	tasklistCasesPage,
	makeAxeBuilder,
}) => {
	await tasklistCasesPage.goto('?search=missing');
	await expect(tasklistCasesPage.noMatchingCasesHeading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the forbidden cases page', async ({
	network,
	tasklistCasesPage,
	forbiddenPage,
	makeAxeBuilder,
}) => {
	network.use(mockQueryProcessInstancesEndpoint({successResponse: new HttpResponse(null, {status: 403})}));

	await tasklistCasesPage.goto();
	await expect(forbiddenPage.heading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations on the generic cases error page', async ({
	network,
	tasklistCasesPage,
	makeAxeBuilder,
}) => {
	network.use(mockQueryProcessInstancesEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

	await tasklistCasesPage.goto();
	await expect(tasklistCasesPage.genericErrorHeading).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});
