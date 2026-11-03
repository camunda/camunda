/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {test, expect} from '#/pw-modules/test-extend';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {mockQueryElementInstancesEndpoint} from '#/shared-test-modules/mock-handlers';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {processInstanceDiagramHandlers} from '#/shared-test-modules/process-instance-diagram-handlers';

const PROCESS_INSTANCE_ID = '2251799813866501';

const HISTORY_ITEMS = [
	createElementInstance({
		elementId: 'start',
		elementInstanceKey: '2251799813685281',
		elementName: 'Start',
		type: 'START_EVENT',
		state: 'COMPLETED',
	}),
	createElementInstance({
		elementId: 'task-1',
		elementInstanceKey: '2251799813685290',
		elementName: 'Review invoice',
		type: 'SERVICE_TASK',
		state: 'ACTIVE',
	}),
];

test.beforeEach(({network}) => {
	network.use(
		mockQueryElementInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryElementInstancesResponse(HISTORY_ITEMS)),
		}),
		...processInstanceDiagramHandlers(),
		...processInstanceHeaderHandlers(
			createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, processDefinitionId: 'my_simple_process'}),
		),
	);
});

test('should match the modification mode page snapshot with staged modifications', async ({
	page,
	operatePreviewProcessInstancePage,
}) => {
	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await page.getByRole('button', {name: `Modify Instance ${PROCESS_INSTANCE_ID}`}).click();
	await page
		.getByRole('dialog', {name: 'Process Instance Modification Mode'})
		.getByRole('button', {name: 'Continue'})
		.click();
	await operatePreviewProcessInstancePage.diagramElement('task-1').click();
	await page.getByRole('button', {name: 'Add single element instance'}).click();

	await expect(page.getByText('Last added modification: Add "Review invoice"')).toBeVisible();
	await expect(page.getByRole('button', {name: 'Review Modifications'})).toBeEnabled();
	await expect(page).toHaveScreenshot();
});

test('should match the modification summary modal snapshot', async ({page, operatePreviewProcessInstancePage}) => {
	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await page.getByRole('button', {name: `Modify Instance ${PROCESS_INSTANCE_ID}`}).click();
	await page
		.getByRole('dialog', {name: 'Process Instance Modification Mode'})
		.getByRole('button', {name: 'Continue'})
		.click();
	await operatePreviewProcessInstancePage.diagramElement('task-1').click();
	await page.getByRole('button', {name: 'Add single element instance'}).click();
	await page.getByRole('button', {name: 'Review Modifications'}).click();

	const summary = page.getByRole('dialog', {name: 'Apply Modifications'});
	await expect(summary).toBeVisible();
	await expect(summary).toHaveScreenshot();
});

test('should match the modification helper modal snapshot', async ({page, operatePreviewProcessInstancePage}) => {
	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await page.getByRole('button', {name: `Modify Instance ${PROCESS_INSTANCE_ID}`}).click();

	const helper = page.getByRole('dialog', {name: 'Process Instance Modification Mode'});
	await expect(helper).toBeVisible();
	await expect(helper).toHaveScreenshot();
});
