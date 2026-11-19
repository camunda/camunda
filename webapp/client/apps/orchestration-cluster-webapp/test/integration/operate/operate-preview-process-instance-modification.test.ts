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
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {processInstanceDiagramHandlers} from '#/shared-test-modules/process-instance-diagram-handlers';
import {
	mockModifyProcessInstanceEndpoint,
	mockQueryElementInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';

const PROCESS_INSTANCE_ID = '2251799813866501';
const PLANNED_TASK_LABEL = /Review invoice, this element instance is planned to be added/;

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

test('should stage, undo, review, and discard modifications', async ({page, operatePreviewProcessInstancePage}) => {
	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await page.getByRole('button', {name: `Modify Instance ${PROCESS_INSTANCE_ID}`}).click();
	const helper = page.getByRole('dialog', {name: 'Process Instance Modification Mode'});
	await helper.getByRole('checkbox', {name: "Don't show this message next time"}).click();
	await helper.getByRole('button', {name: 'Continue'}).click();
	const review = page.getByRole('button', {name: 'Review Modifications'});
	await expect(review).toBeDisabled();

	await operatePreviewProcessInstancePage.diagramElement('task-1').click();
	await page.getByRole('button', {name: 'Add single element instance'}).click();
	await expect(page.getByText('Last added modification: Add "Review invoice"')).toBeVisible();
	await expect(operatePreviewProcessInstancePage.historyItem(PLANNED_TASK_LABEL)).toBeVisible();
	await page.getByRole('button', {name: 'Undo', exact: true}).click();
	await expect(review).toBeDisabled();
	await expect(operatePreviewProcessInstancePage.historyItem(PLANNED_TASK_LABEL)).not.toBeVisible();

	await operatePreviewProcessInstancePage.diagramElement('task-1').click();
	await page.getByRole('button', {name: 'Add single element instance'}).click();
	await review.click();
	const summary = page.getByRole('dialog', {name: 'Apply Modifications'});
	await expect(summary.getByRole('row').filter({hasText: 'Review invoice'})).toContainText('Add');
	await expect(summary.getByText('No planned variable modifications')).toBeVisible();
	await summary.getByRole('button', {name: 'Cancel', exact: true}).click();

	await page.getByRole('button', {name: 'Discard All'}).click();
	await page
		.getByRole('alertdialog', {name: 'Discard Modifications'})
		.getByRole('button', {name: 'Discard', exact: true})
		.click();

	await expect(page.getByRole('button', {name: `Modify Instance ${PROCESS_INSTANCE_ID}`})).toBeVisible();
	await expect(review).not.toBeVisible();
	await page.getByRole('button', {name: `Modify Instance ${PROCESS_INSTANCE_ID}`}).click();
	await expect(helper).not.toBeVisible();
	await expect(review).toBeDisabled();
});

test('should apply staged modifications and leave modification mode', async ({
	network,
	page,
	operatePreviewProcessInstancePage,
}) => {
	network.use(mockModifyProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204})}));
	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await page.getByRole('button', {name: `Modify Instance ${PROCESS_INSTANCE_ID}`}).click();
	await page
		.getByRole('dialog', {name: 'Process Instance Modification Mode'})
		.getByRole('button', {name: 'Continue'})
		.click();
	await operatePreviewProcessInstancePage.diagramElement('task-1').click();
	await page.getByRole('button', {name: 'Add single element instance'}).click();

	await page.getByRole('button', {name: 'Review Modifications'}).click();
	await page.getByRole('dialog', {name: 'Apply Modifications'}).getByRole('button', {name: 'Apply'}).click();

	await expect(page.getByText('Modifications applied')).toBeVisible();
	await expect(page.getByRole('button', {name: 'Review Modifications'})).not.toBeVisible();
	await expect(page.getByRole('button', {name: `Modify Instance ${PROCESS_INSTANCE_ID}`})).toBeVisible();
});

test('should warn before leaving modification mode with staged modifications', async ({
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

	await page.getByRole('link', {name: 'Processes', exact: true}).click();

	await expect(page.getByRole('alertdialog')).toBeVisible();
	await page.getByRole('alertdialog').getByRole('button', {name: 'Stay'}).click();
	await expect(page.getByRole('button', {name: 'Review Modifications'})).toBeVisible();
});
