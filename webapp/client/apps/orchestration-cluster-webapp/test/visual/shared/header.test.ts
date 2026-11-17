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
	mockC3ClustersEndpoint,
	mockC3NotificationEventsEndpoint,
	mockC3NotificationsEndpoint,
	mockC3OrganizationsEndpoint,
	mockCurrentUserEndpoint,
	mockLicenseEndpoint,
	mockQueryUserTasksEndpoint,
	mockSaasTokenEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {
	SAAS_CLUSTER_ID,
	SAAS_ORGANIZATION_ID,
	createC3Cluster,
	createC3Organization,
	createSaasUserToken,
} from '#/shared-test-modules/api-mocks/saas';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createQueryUserTasksResponse} from '#/shared-test-modules/api-mocks/user-tasks';

test.beforeEach(async ({network, tasklistIndexPage}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense({licenseType: 'saas'}))}),
		mockQueryUserTasksEndpoint({successResponse: HttpResponse.json(createQueryUserTasksResponse())}),
		mockSaasTokenEndpoint({successResponse: HttpResponse.json(createSaasUserToken())}),
		mockC3OrganizationsEndpoint({successResponse: HttpResponse.json([createC3Organization()])}),
		mockC3NotificationsEndpoint({successResponse: HttpResponse.json([])}),
		mockC3NotificationEventsEndpoint({
			successResponse: new HttpResponse('', {headers: {'Content-Type': 'text/event-stream'}}),
		}),
	);
	await tasklistIndexPage.seedSaasBootConfig({organizationId: SAAS_ORGANIZATION_ID, clusterId: SAAS_CLUSTER_ID});
});

test('should match the header snapshot with the dev environment badge', async ({network, tasklistIndexPage}) => {
	network.use(
		mockC3ClustersEndpoint({successResponse: HttpResponse.json([createC3Cluster({labels: {camunda: ['dev']}})])}),
	);

	await tasklistIndexPage.goto();
	await expect(tasklistIndexPage.header.environmentBadge('dev')).toBeVisible();

	await expect(tasklistIndexPage.header.banner).toHaveScreenshot();
});

test('should match the header snapshot with the test environment badge', async ({network, tasklistIndexPage}) => {
	network.use(
		mockC3ClustersEndpoint({successResponse: HttpResponse.json([createC3Cluster({labels: {camunda: ['test']}})])}),
	);

	await tasklistIndexPage.goto();
	await expect(tasklistIndexPage.header.environmentBadge('test')).toBeVisible();

	await expect(tasklistIndexPage.header.banner).toHaveScreenshot();
});

test('should match the header snapshot with the stage environment badge', async ({network, tasklistIndexPage}) => {
	network.use(
		mockC3ClustersEndpoint({successResponse: HttpResponse.json([createC3Cluster({labels: {camunda: ['stage']}})])}),
	);

	await tasklistIndexPage.goto();
	await expect(tasklistIndexPage.header.environmentBadge('stage')).toBeVisible();

	await expect(tasklistIndexPage.header.banner).toHaveScreenshot();
});

test('should match the header snapshot with the prod environment badge', async ({network, tasklistIndexPage}) => {
	network.use(
		mockC3ClustersEndpoint({successResponse: HttpResponse.json([createC3Cluster({labels: {camunda: ['prod']}})])}),
	);

	await tasklistIndexPage.goto();
	await expect(tasklistIndexPage.header.environmentBadge('prod')).toBeVisible();

	await expect(tasklistIndexPage.header.banner).toHaveScreenshot();
});
