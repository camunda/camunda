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

test('should have no accessibility violations in modification mode with the dropdown and the summary modal', async ({
	page,
	operatePreviewProcessInstancePage,
	makeAxeBuilder,
}) => {
	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await page.getByRole('button', {name: `Modify Instance ${PROCESS_INSTANCE_ID}`}).click();
	const helper = page.getByRole('dialog', {name: 'Process Instance Modification Mode'});
	await expect(helper).toBeVisible();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);

	await helper.getByRole('button', {name: 'Continue'}).click();
	await operatePreviewProcessInstancePage.diagramElement('task-1').click();
	await expect(page.getByText('Element Modifications')).toBeVisible();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);

	await page.getByRole('button', {name: 'Add single element instance'}).click();
	await expect(operatePreviewProcessInstancePage.historyItem(/planned to be added/)).toBeVisible();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);

	await page.getByRole('button', {name: 'Review Modifications'}).click();
	await expect(page.getByRole('dialog', {name: 'Apply Modifications'})).toBeVisible();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
});
