/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import type {Locator} from '@playwright/test';
import {queryProcessDefinitionsRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.11';
import {test, expect} from '#/pw-modules/test-extend';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {
	createQueryProcessDefinitionsResponse,
	createProcessDefinition,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {
	createGetProcessDefinitionStatisticsResponse,
	createProcessDefinitionStatistic,
} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createBatchOperationItem,
	createQueryBatchOperationItemsResponse,
} from '#/shared-test-modules/api-mocks/batch-operations';
import {
	mockCurrentUserEndpoint,
	mockSystemConfigurationEndpoint,
	mockLicenseEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
} from '#/shared-test-modules/mock-handlers';

async function waitForModalAnimations(dialog: Locator) {
	await dialog.evaluate(async (node) => {
		type AnimatedElement = {parentElement: AnimatedElement | null; getAnimations: () => {finished: Promise<unknown>}[]};
		for (let element: AnimatedElement | null = node; element !== null; element = element.parentElement) {
			await Promise.all(element.getAnimations().map((animation) => animation.finished));
		}
	});
}

test.beforeEach(({network}) => {
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
		mockQueryProcessDefinitionsEndpoint({successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse())}),
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({items: [createProcessInstance({processInstanceKey: '1'})]}),
			),
		}),
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse()),
		}),
	);
});

test('should have no accessibility violations in the bulk toolbar and confirmation', async ({
	page,
	operateProcessesPage,
	makeAxeBuilder,
}) => {
	await operateProcessesPage.goto();
	await page.getByRole('checkbox', {name: 'Select all items'}).check({force: true});
	await expect(page.getByRole('button', {name: 'Cancel', exact: true})).toBeVisible();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
	await page.getByRole('button', {name: 'Cancel', exact: true}).click();
	await expect(page.getByRole('dialog')).toBeVisible();
	await waitForModalAnimations(page.getByRole('dialog'));
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
});

test('should have no accessibility violations in batch modification mode and review', async ({
	network,
	page,
	operateProcessesPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [createProcessDefinition({processDefinitionId: 'my_simple_process', processDefinitionKey: '123'})],
				}),
			),
		}),
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
		mockGetProcessDefinitionStatisticsEndpoint({
			successResponse: HttpResponse.json(
				createGetProcessDefinitionStatisticsResponse([
					createProcessDefinitionStatistic({elementId: 'task-1', active: 1}),
				]),
			),
		}),
	);
	await operateProcessesPage.goto('?process=my_simple_process&version=1&elementId=task-1');
	await page.getByRole('checkbox', {name: 'Select instance 1'}).check({force: true});
	await operateProcessesPage.moveButton.click();
	await expect(page.getByRole('dialog')).toBeVisible();
	await waitForModalAnimations(page.getByRole('dialog'));
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
	await page.getByRole('dialog').getByRole('button', {name: 'Continue'}).click();
	await operateProcessesPage.targetElement('end_event').click();
	await operateProcessesPage.reviewModificationButton.click();
	await expect(page.getByRole('dialog')).toBeVisible();
	await waitForModalAnimations(page.getByRole('dialog'));
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
	await page.getByRole('dialog').getByRole('button', {name: 'Cancel'}).click();
	await expect(page.getByRole('dialog')).not.toBeVisible();
	await page.getByRole('link', {name: 'View instance 1'}).click();
	const exitConfirmation = page.getByRole('dialog', {name: 'Exit batch modification mode'});
	await expect(exitConfirmation).toBeVisible();
	await waitForModalAnimations(exitConfirmation);
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
});

test('should expose failed operation details accessibly', async ({
	network,
	page,
	operateProcessesPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockQueryBatchOperationItemsEndpoint({
			successResponse: HttpResponse.json(
				createQueryBatchOperationItemsResponse({
					items: [
						createBatchOperationItem({
							processInstanceKey: '1',
							state: 'FAILED',
							errorMessage: 'Unable to complete operation',
						}),
					],
				}),
			),
		}),
	);

	await operateProcessesPage.goto('?batchOperationKey=2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee');
	const expand = page.getByRole('button', {name: 'Show failure details for instance 1'});
	await expect(expand).toBeVisible();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);

	await expand.click();
	await expect(expand).toHaveAttribute('aria-expanded', 'true');
	await expect(page.getByText('Unable to complete operation')).toBeVisible();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
});

test('should have no accessibility violations in the process definition deletion confirmation', async ({
	network,
	page,
	operateProcessesPage,
	makeAxeBuilder,
}) => {
	network.use(
		mockQueryProcessDefinitionsEndpoint({
			schema: queryProcessDefinitionsRequestBodySchema.refine((body) => body.filter?.state === 'DRAINING'),
			successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			failureResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process'})],
				}),
			),
		}),
		mockQueryProcessInstancesEndpoint({successResponse: HttpResponse.json(createQueryProcessInstancesResponse())}),
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
		mockGetProcessDefinitionStatisticsEndpoint({
			successResponse: HttpResponse.json(createGetProcessDefinitionStatisticsResponse([])),
		}),
	);
	await operateProcessesPage.goto('?process=order-process&version=1');
	await page.getByRole('button', {name: 'Delete Process Definition "Order Process - Version 1"'}).click();
	await expect(page.getByRole('dialog')).toBeVisible();
	await page.getByRole('dialog').evaluate(async (dialog) => {
		type AnimatedElement = {
			parentElement: AnimatedElement | null;
			getAnimations: () => {finished: Promise<unknown>}[];
		};
		for (let element: AnimatedElement | null = dialog; element !== null; element = element.parentElement) {
			await Promise.all(element.getAnimations().map((animation) => animation.finished));
		}
	});
	expect((await makeAxeBuilder().include('[role="dialog"]').analyze()).violations).toEqual([]);
});
