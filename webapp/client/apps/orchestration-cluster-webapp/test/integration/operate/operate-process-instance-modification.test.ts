/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import isEqual from 'lodash/isEqual.js';
import {z} from 'zod';
import {test, expect} from '#/pw-modules/test-extend';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createVariable} from '#/shared-test-modules/api-mocks/variables';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {mockModifyProcessInstanceEndpoint, mockQueryVariablesEndpoint} from '#/shared-test-modules/mock-handlers';

const PLANNED_TASK_LABEL = 'Review invoice, this element instance is planned to be added';

test('should stage, undo, review and discard modifications with the default activation flag', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({
		processInstanceKey: '2251799813866401',
		processDefinitionId: 'my_simple_process',
	});
	network.use(...processInstanceHeaderHandlers(instance));
	await operateProcessInstancePage.goto(instance.processInstanceKey, '/variables');
	await operateProcessInstancePage.action('Modify', instance.processInstanceKey).click();
	const helper = operateProcessInstancePage.dialog('Process Instance Modification Mode');
	await helper.getByText("Don't show this message next time").click();
	await helper.getByRole('button', {name: 'Continue'}).click();
	const review = page.getByRole('button', {name: 'Review Modifications'});
	await expect(review).toBeDisabled();

	await operateProcessInstancePage.diagramElement('task-1').click();
	await page.getByRole('button', {name: 'Add single element instance'}).click();
	await expect(page.getByText('Last added modification: Add "Review invoice"')).toBeVisible();
	await page.getByRole('button', {name: 'Undo', exact: true}).click();
	await expect(review).toBeDisabled();
	await expect(operateProcessInstancePage.historyItem(PLANNED_TASK_LABEL)).not.toBeVisible();

	await operateProcessInstancePage.diagramElement('task-1').click();
	await page.getByRole('button', {name: 'Add single element instance'}).click();
	await operateProcessInstancePage.historyItem(PLANNED_TASK_LABEL).click();
	await page.getByRole('button', {name: 'Add Variable'}).click();
	await page.getByTestId('new-variable-name').fill('foo');
	const value = page.getByRole('group', {name: 'Value', exact: true});
	await expect(value.getByRole('textbox')).toBeVisible();
	await value.click();
	await expect(value.getByRole('textbox')).toBeFocused();
	await page.keyboard.type('1');
	await page.keyboard.press('Tab');
	await expect(page.getByText('Add new variable "foo"', {exact: true})).toBeVisible();
	await page.getByRole('button', {name: 'Undo', exact: true}).click();
	await expect(page.getByTestId('new-variable-name')).not.toBeVisible();
	await expect(page.getByText('Last added modification: Add "Review invoice"')).toBeVisible();

	await review.click();
	const summary = operateProcessInstancePage.dialog('Apply Modifications');
	await expect(summary.getByRole('row').filter({hasText: 'Review invoice'})).toContainText('Add');
	await expect(summary.getByText('No planned variable modifications')).toBeVisible();
	await summary.getByRole('button', {name: 'Cancel', exact: true}).click();
	await page.getByRole('button', {name: 'Discard All'}).click();
	await operateProcessInstancePage.dialog('Discard Modifications').getByRole('button', {name: 'Discard'}).click();

	await expect(operateProcessInstancePage.action('Modify', instance.processInstanceKey)).toBeVisible();
	await expect(review).not.toBeVisible();
	await operateProcessInstancePage.action('Modify', instance.processInstanceKey).click();
	await expect(helper).not.toBeVisible();
	await expect(review).toBeDisabled();
	await expect(operateProcessInstancePage.historyItem(PLANNED_TASK_LABEL)).not.toBeVisible();
});

test('should apply staged token and local/root variable changes from the collapsed default Modify menu', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({
		processInstanceKey: '2251799813866402',
		processDefinitionId: 'my_simple_process',
	});
	const rootVariable = createVariable({
		name: 'amount',
		value: '100',
		processInstanceKey: instance.processInstanceKey,
		scopeKey: instance.processInstanceKey,
	});
	const expectedPayload = {
		activateInstructions: [
			{
				elementId: 'task-1',
				variableInstructions: [{variables: {foo: 1}, scopeId: 'task-1'}, {variables: {amount: 1001}}],
			},
		],
		moveInstructions: [],
		terminateInstructions: [],
	};
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockQueryVariablesEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: [rootVariable]})),
		}),
		mockModifyProcessInstanceEndpoint({
			schema: z.custom((body) => isEqual(body, expectedPayload)),
			successResponse: new HttpResponse(null, {status: 204}),
			failureResponse: new HttpResponse(null, {status: 400}),
		}),
	);
	await page.setViewportSize({width: 1024, height: 768});
	await operateProcessInstancePage.goto(instance.processInstanceKey, '/variables');
	await operateProcessInstancePage.actionsMenu.click();
	await page.getByRole('menuitem', {name: 'Modify', exact: true}).click();
	await operateProcessInstancePage
		.dialog('Process Instance Modification Mode')
		.getByRole('button', {name: 'Continue'})
		.click();
	await expect(page.getByRole('button', {name: 'Review Modifications'})).toBeDisabled();
	await expect(page.getByRole('button', {name: 'Add Variable'})).not.toBeVisible();
	await page.setViewportSize({width: 1920, height: 1080});

	await operateProcessInstancePage.diagramElement('task-1').click();
	await page.getByRole('button', {name: 'Add single element instance'}).click();
	await operateProcessInstancePage.historyItem(PLANNED_TASK_LABEL).click();
	await page.getByRole('button', {name: 'Add Variable'}).click();
	await page.getByTestId('new-variable-name').fill('foo');
	const value = page.getByRole('group', {name: 'Value', exact: true});
	await expect(value.getByRole('textbox')).toBeVisible();
	await value.click();
	await expect(value.getByRole('textbox')).toBeFocused();
	await page.keyboard.type('1');
	await page.keyboard.press('Tab');
	await expect(page.getByText('Add new variable "foo"', {exact: true})).toBeVisible();

	await operateProcessInstancePage.historyTree.getByText('My Process', {exact: true}).click();
	await expect(operateProcessInstancePage.historyItem('My Process')).toHaveAttribute('aria-selected', 'true');
	await operateProcessInstancePage.variableRow('amount').getByText('100', {exact: true}).click();
	await expect(operateProcessInstancePage.variableRow('amount').getByRole('textbox')).toBeFocused();
	await page.keyboard.press('End');
	await page.keyboard.type('1');
	await page.keyboard.press('Tab');
	await expect(page.getByText('Edit variable "amount"', {exact: true})).toBeVisible();
	await page.getByRole('button', {name: 'Review Modifications'}).click();
	const summary = operateProcessInstancePage.dialog('Apply Modifications');
	await expect(summary.getByRole('row').filter({hasText: 'Review invoice'}).first()).toContainText('Add');
	await expect(summary.getByRole('row').filter({hasText: 'foo: 1'})).toContainText('Review invoice');
	await expect(summary.getByRole('row').filter({hasText: 'amount: 1001'})).toContainText('Edit');
	network.use(
		mockQueryVariablesEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: [{...rootVariable, value: '1001'}]})),
		}),
	);
	const accepted = page.waitForResponse(
		(response) =>
			response.url().endsWith(`/process-instances/${instance.processInstanceKey}/modification`) &&
			response.request().method() === 'POST',
	);
	await summary.getByRole('button', {name: 'Apply', exact: true}).click();

	expect((await accepted).status()).toBe(204);
	await expect(page.getByText('Modifications applied', {exact: true})).toBeVisible();
	await expect(page.getByRole('button', {name: 'Review Modifications'})).not.toBeVisible();
	await expect(operateProcessInstancePage.variableRow('amount').getByRole('button', {name: 'Copy amount'})).toHaveText(
		'1001',
	);
	await page.setViewportSize({width: 1024, height: 768});
	await operateProcessInstancePage.actionsMenu.click();
	await expect(page.getByRole('menuitem', {name: 'Modify', exact: true})).toBeEnabled();
});
