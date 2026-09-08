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
	mockSystemConfigurationEndpoint,
	mockLicenseEndpoint,
	mockGetProcessInstanceEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {createQueryProcessDefinitionsResponse} from '#/shared-test-modules/api-mocks/process-definitions';

test('should open an instance from Processes, retain a deep link on reload and return to its version', async ({
	page,
	network,
	operateProcessesPage,
	makeAxeBuilder,
}) => {
	const instance = createProcessInstance();
	network.use(
		mockGetProcessInstanceWaitStateStatisticsEndpoint({successResponse: HttpResponse.json({items: []})}),
		mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(instance)}),
		mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}),
		mockGetProcessDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(
				'<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test"><process id="my-process" /></definitions>',
			),
		}),
		mockQueryProcessDefinitionsEndpoint({successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse())}),
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryProcessInstancesResponse({items: [instance]})),
		}),
	);
	await operateProcessesPage.goto();
	await operateProcessesPage.instanceLink(instance.processInstanceKey).click();
	await expect(page).toHaveURL(new RegExp(`/operate/processes/${instance.processInstanceKey}$`));
	await expect(page.getByRole('heading', {name: 'Operate Process Instance', exact: true})).toBeAttached();
	await expect(page.getByRole('cell', {name: instance.processInstanceKey, exact: true})).toBeVisible();
	await expect(page).toHaveTitle(`Operate: Process Instance ${instance.processInstanceKey} of My Process`);
	await page.reload();
	await expect(page.getByText('My Process', {exact: true})).toBeVisible();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
	await page.getByRole('link', {name: /View process.*version 1/}).click();
	await expect(operateProcessesPage.filtersPanel).toBeVisible();
	expect(new URL(page.url()).searchParams.get('process')).toBe('my-process');
	expect(new URL(page.url()).searchParams.get('version')).toBe('1');
});
