/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {test, expect} from '#/pw-modules/test-extend';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createElementInstance} from '#/shared-test-modules/api-mocks/element-instances';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {mockQueryElementInstancesEndpoint} from '#/shared-test-modules/mock-handlers';

test('should keep the history tree and responsive tabs accessible', async ({
	network,
	page,
	operateProcessInstancePage,
	makeAxeBuilder,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813756061', state: 'COMPLETED'});
	const element = createElementInstance({
		elementInstanceKey: '2251799813756062',
		processInstanceKey: instance.processInstanceKey,
		elementInstanceScopeKey: instance.processInstanceKey,
		elementId: 'task-1',
		elementName: 'Review invoice',
		state: 'COMPLETED',
	});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockQueryElementInstancesEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: [element]})),
		}),
	);
	await operateProcessInstancePage.goto(instance.processInstanceKey, '/variables');
	await expect(operateProcessInstancePage.historyTree).toBeVisible();
	await expect(operateProcessInstancePage.historyTree.getByText('Review invoice', {exact: true})).toBeVisible();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
	await page.setViewportSize({width: 800, height: 768});
	await operateProcessInstancePage.historyTab.click();
	await expect(operateProcessInstancePage.historyTree).toBeVisible();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
});
