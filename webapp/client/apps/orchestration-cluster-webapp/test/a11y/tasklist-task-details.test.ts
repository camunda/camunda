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
	mockGetProcessDefinitionXmlEndpoint,
	mockGetUserTaskEndpoint,
	mockLicenseEndpoint,
	mockQueryUserTasksEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({username: 'demo'})),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['tasklist']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockQueryUserTasksEndpoint({
			successResponse: HttpResponse.json(createQueryUserTasksResponse()),
		}),
		mockGetUserTaskEndpoint({
			successResponse: HttpResponse.json(
				createUserTask({
					state: 'CREATED',
					name: 'Review purchase order',
					processName: 'Procurement process',
					elementId: 'task-1',
					assignee: 'demo',
				}),
			),
		}),
	);
});

test('should have no accessibility violations on the Process tab', async ({
	network,
	taskDetailPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockGetProcessDefinitionXmlEndpoint({
			successResponse: new HttpResponse(BPMN_XML, {headers: {'Content-Type': 'text/xml'}}),
		}),
	);

	await taskDetailPage.gotoProcess('2251799813685281');
	await expect(taskDetailPage.processDiagramZoomReset).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});

test('should have no accessibility violations when process access is forbidden', async ({
	network,
	taskDetailPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockGetProcessDefinitionXmlEndpoint({
			successResponse: new HttpResponse(null, {status: 403}),
		}),
	);

	await taskDetailPage.gotoProcess('2251799813685281');
	await expect(taskDetailPage.processForbiddenError).toBeVisible();

	const accessibilityScanResults = await makeAxeBuilder().analyze();
	expect(accessibilityScanResults.violations).toEqual([]);
});
