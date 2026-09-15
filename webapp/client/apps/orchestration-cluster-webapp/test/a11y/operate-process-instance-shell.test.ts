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
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {createQueryProcessDefinitionsResponse} from '#/shared-test-modules/api-mocks/process-definitions';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {
	mockCurrentUserEndpoint,
	mockSystemConfigurationEndpoint,
	mockLicenseEndpoint,
	mockGetProcessInstanceEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
} from '#/shared-test-modules/mock-handlers';

const PROCESS_INSTANCE_ID = '2251799813685280';
const PROCESS_XML =
	'<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="my-process"><callActivity id="call-activity" /></process></definitions>';

test('should have no accessibility violations on the process instance shell page', async ({
	network,
	page,
	makeAxeBuilder,
}) => {
	network.use(
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockGetProcessDefinitionInstanceStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockGetProcessInstanceEndpoint({
			successResponse: HttpResponse.json(
				createProcessInstance({
					processInstanceKey: PROCESS_INSTANCE_ID,
					parentProcessInstanceKey: '2251799813685279',
				}),
			),
		}),
		mockGetProcessInstanceCallHierarchyEndpoint({
			successResponse: HttpResponse.json([
				createCallHierarchy({processInstanceKey: '2251799813685279', processDefinitionName: 'Parent Process'}),
				createCallHierarchy({processInstanceKey: PROCESS_INSTANCE_ID, processDefinitionName: 'My Process'}),
			]),
		}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
		}),
		mockGetProcessInstanceWaitStateStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(PROCESS_XML)}),
	);

	await page.goto(`/operate/processes/${PROCESS_INSTANCE_ID}`);
	await expect(page).toHaveURL(`/operate/processes/${PROCESS_INSTANCE_ID}/variables`);
	await expect(page.getByRole('heading', {name: 'Operate Process Instance'})).toBeAttached();
	await page.getByRole('link', {name: 'Dashboard', exact: true}).hover();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
});
