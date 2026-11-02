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
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessInstanceEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockLicenseEndpoint,
	mockOperateClientConfigEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createOperateClientConfigScript} from '#/shared-test-modules/api-mocks/operate-client-config';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';

const PROCESS_INSTANCE_ID = '2251799813685280';

test('should have no accessibility violations in the process instance placeholder shell', async ({
	network,
	operatePreviewProcessInstancePage,
	makeAxeBuilder,
}) => {
	network.use(
		...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockOperateClientConfigEndpoint({
			successResponse: HttpResponse.text(createOperateClientConfigScript()),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockGetProcessInstanceEndpoint({
			successResponse: HttpResponse.json(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
		}),
		mockGetProcessInstanceWaitStateStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
		}),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await expect(operatePreviewProcessInstancePage.header).toBeVisible();

	const results = await makeAxeBuilder().analyze();
	expect(results.violations).toEqual([]);
});

test('should have no accessibility violations with the breadcrumb overflow menu open', async ({
	network,
	page,
	operatePreviewProcessInstancePage,
	makeAxeBuilder,
}) => {
	const hierarchy = ['1', '2', '3', '4', '5', PROCESS_INSTANCE_ID].map((processInstanceKey) =>
		createCallHierarchy({processInstanceKey, processDefinitionName: `Process ${processInstanceKey}`}),
	);
	network.use(
		mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json(hierarchy)}),
		...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await page.getByRole('button', {name: 'More'}).click();
	await expect(page.getByRole('menuitem', {name: 'Process 3'})).toBeVisible();

	const results = await makeAxeBuilder().include('[role="menu"]').analyze();
	expect(results.violations).toEqual([]);
});
