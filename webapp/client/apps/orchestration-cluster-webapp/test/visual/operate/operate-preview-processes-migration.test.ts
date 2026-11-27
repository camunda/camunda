/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {movePointerAwayFromNavigation} from './movePointerAwayFromNavigation';
import {HttpResponse} from 'msw';
import {
	mockCurrentUserEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
	mockGetProcessDefinitionXmlByKeyEndpoint,
	mockLicenseEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createGetProcessDefinitionStatisticsResponse,
	createProcessDefinitionStatistic,
} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {BPMN_XML, MIGRATION_SOURCE_BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';

const INSTANCE_KEY = '2251799813700001';
const NOW = new Date('2026-01-15T10:15:00.000Z');
const SOURCE = createProcessDefinition({
	processDefinitionId: 'my_simple_process',
	processDefinitionKey: 'source-key',
	name: 'Invoice process',
	version: 1,
});
const TARGET = createProcessDefinition({
	processDefinitionId: 'my_simple_process',
	processDefinitionKey: 'latest-key',
	name: 'Invoice process',
	version: 2,
});

test.beforeEach(async ({network, page}) => {
	await page.clock.setFixedTime(NOW);

	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse({items: [SOURCE, TARGET]})),
		}),
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({
					items: [createProcessInstance({processInstanceKey: INSTANCE_KEY, state: 'ACTIVE'})],
				}),
			),
		}),
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
		}),
		mockGetProcessDefinitionXmlByKeyEndpoint({
			xmlByKey: {'source-key': MIGRATION_SOURCE_BPMN_XML, 'latest-key': BPMN_XML},
		}),
		mockGetProcessDefinitionStatisticsEndpoint({
			successResponse: HttpResponse.json(
				createGetProcessDefinitionStatisticsResponse([
					createProcessDefinitionStatistic({elementId: 'task-1', active: 2}),
				]),
			),
		}),
	);
});

async function enterMigrationMode(page: import('@playwright/test').Page) {
	await page.goto('/operate-preview/processes?process=my_simple_process&version=1&active=true');
	await page.getByRole('checkbox', {name: `Select instance ${INSTANCE_KEY}`}).click({force: true});
	await page.getByRole('button', {name: 'Migrate'}).click();
	await page.getByRole('dialog').getByRole('button', {name: 'Continue'}).click();
	await expect(page.getByText('Migration step 1 - mapping elements')).toBeVisible();
}

test('should match the DS migration mode snapshot', async ({page}) => {
	await enterMigrationMode(page);

	await expect(page.getByRole('button', {name: 'Next'})).toBeEnabled();

	await movePointerAwayFromNavigation(page);
	await expect(page).toHaveScreenshot();
});
