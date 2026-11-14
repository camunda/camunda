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

test('should match the cases table snapshot', async ({network, tasklistCasesPage, page}) => {
	network.use(
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [
						createProcessInstance({
							processInstanceKey: '2251799813685301',
							processDefinitionName: 'Home loan application',
							businessId: 'HL-28005',
							startDate: '2024-01-15T10:00:00.000Z',
						}),
						createProcessInstance({
							processInstanceKey: '2251799813685302',
							processDefinitionName: 'Insurance claim',
							businessId: 'IC-28011',
							startDate: '2024-01-14T09:30:00.000Z',
							hasIncident: true,
						}),
					],
				}),
			),
		}),
	);

	await tasklistCasesPage.goto();
	await expect(tasklistCasesPage.caseLink('Home loan application, HL-28005')).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the no open cases snapshot', async ({tasklistCasesPage, page}) => {
	await tasklistCasesPage.goto();
	await expect(tasklistCasesPage.noOpenCasesHeading).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the no matching cases snapshot', async ({tasklistCasesPage, page}) => {
	await tasklistCasesPage.goto('?search=missing');
	await expect(tasklistCasesPage.noMatchingCasesHeading).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the forbidden cases page snapshot', async ({network, tasklistCasesPage, forbiddenPage, page}) => {
	network.use(mockQueryProcessInstancesEndpoint({successResponse: new HttpResponse(null, {status: 403})}));

	await tasklistCasesPage.goto();
	await expect(forbiddenPage.heading).toBeVisible();

	await expect(page).toHaveScreenshot();
});

test('should match the generic cases error page snapshot', async ({network, tasklistCasesPage, page}) => {
	network.use(mockQueryProcessInstancesEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

	await tasklistCasesPage.goto();
	await expect(tasklistCasesPage.genericErrorHeading).toBeVisible();

	await expect(page).toHaveScreenshot();
});
