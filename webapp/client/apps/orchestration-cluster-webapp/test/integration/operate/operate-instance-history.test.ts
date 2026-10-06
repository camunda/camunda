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
import {mockQueryElementInstancesEndpoint, mockGetElementInstanceEndpoint} from '#/shared-test-modules/mock-handlers';

test('should select history instances from a direct tenant URL and clear only selection from the root', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({
		processInstanceKey: '2251799813756031',
		state: 'COMPLETED',
		tenantId: 'tenant-a',
	});
	const element = createElementInstance({
		elementInstanceKey: '2251799813756032',
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
		mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(element)}),
	);
	await operateProcessInstancePage.goto(instance.processInstanceKey, '/variables?tenantId=tenant-a');
	await expect(operateProcessInstancePage.historyTree).toBeVisible();
	await expect(page).toHaveTitle(`Operate: Process Instance ${instance.processInstanceKey} of My Process`);
	await operateProcessInstancePage.historyTree.getByText('Review invoice', {exact: true}).click();
	await expect(page).toHaveURL(new RegExp(`/details\\?.*elementInstanceKey=%22${element.elementInstanceKey}%22`));
	expect(new URL(page.url()).searchParams.get('tenantId')).toBe('tenant-a');
	await page.reload();
	await expect(operateProcessInstancePage.historyTree.getByText('Review invoice', {exact: true})).toBeVisible();
	expect(new URL(page.url()).searchParams.get('elementInstanceKey')).toBe(JSON.stringify(element.elementInstanceKey));
	await operateProcessInstancePage.historyTree.getByText('My Process', {exact: true}).click();
	await expect(page).toHaveURL(`/operate/processes/${instance.processInstanceKey}/details?tenantId=tenant-a`);
});

test('should keep selection across mobile history tabs and restore the desktop split', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813756041', state: 'COMPLETED'});
	const element = createElementInstance({
		elementInstanceKey: '2251799813756042',
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
		mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(element)}),
	);
	await page.setViewportSize({width: 800, height: 768});
	await operateProcessInstancePage.goto(
		instance.processInstanceKey,
		`/history?elementId=task-1&elementInstanceKey=${element.elementInstanceKey}`,
	);
	await expect(operateProcessInstancePage.historyTree).toBeVisible();
	await expect(operateProcessInstancePage.timestamps).not.toBeVisible();
	await page.getByRole('link', {name: 'Variables', exact: true}).click();
	await expect(operateProcessInstancePage.historyTree).not.toBeVisible();
	expect(new URL(page.url()).searchParams.get('elementInstanceKey')).toBe(JSON.stringify(element.elementInstanceKey));
	await operateProcessInstancePage.historyTab.click();
	await expect(operateProcessInstancePage.historyTree.getByText('Review invoice', {exact: true})).toBeVisible();
	await page.setViewportSize({width: 1920, height: 1080});
	await expect(page).toHaveURL(new RegExp(`/details\\?.*elementInstanceKey=%22${element.elementInstanceKey}%22`));
	await expect(page.getByRole('link', {name: 'Details', exact: true})).toHaveAttribute('aria-current', 'page');
	await expect(operateProcessInstancePage.timestamps).toBeVisible();
	await expect(operateProcessInstancePage.executionCount).toBeVisible();
	await expect(operateProcessInstancePage.historyTree).toHaveCount(1);
});

test('should show pending history, recover a failed read, and propagate forbidden history without hiding the page', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813756051', state: 'COMPLETED'});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockQueryElementInstancesEndpoint({
			successResponse: new HttpResponse(null, {status: 500}),
			delay: 1000,
		}),
	);
	await operateProcessInstancePage.goto(instance.processInstanceKey, '/variables');
	await expect(page.getByRole('heading', {name: 'Operate Process Instance'})).toBeAttached();
	await expect(operateProcessInstancePage.historyTree.getByText('My Process', {exact: true})).not.toBeVisible();
	await expect(page.getByText('Instance history could not be fetched. Please refresh.', {exact: true})).toBeVisible();
	network.use(mockQueryElementInstancesEndpoint({successResponse: HttpResponse.json(createPaginatedResponse())}));
	await operateProcessInstancePage.historyRetry.click();
	await expect(operateProcessInstancePage.historyTree.getByText('My Process', {exact: true})).toBeVisible();
	network.use(mockQueryElementInstancesEndpoint({successResponse: new HttpResponse(null, {status: 403})}));
	await page.reload();
	await expect(page.getByText('You do not have permission to view instance history', {exact: true})).toBeVisible();
	await expect(page.getByRole('heading', {name: 'Operate Process Instance'})).toBeAttached();
});
