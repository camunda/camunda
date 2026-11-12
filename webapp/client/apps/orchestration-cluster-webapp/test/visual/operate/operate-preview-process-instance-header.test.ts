/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {mockGetProcessInstanceCallHierarchyEndpoint} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';

const PROCESS_INSTANCE_ID = '2251799813685280';
const NARROW_VIEWPORT = {width: 1000, height: 800};

function callHierarchyOf(names: string[]) {
	return names.map((name, index) =>
		createCallHierarchy({
			processInstanceKey: index === names.length - 1 ? PROCESS_INSTANCE_ID : String(index + 1),
			processDefinitionName: name,
		}),
	);
}

test('should match the header snapshot of a running instance with an incident', async ({
	network,
	operatePreviewProcessInstancePage,
	page,
}) => {
	network.use(
		...processInstanceHeaderHandlers(
			createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: true}),
		),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

	await expect(operatePreviewProcessInstancePage.header.getByText('My Process')).toBeVisible();
	await expect(page).toHaveScreenshot();
});

test('should match the header snapshot of a completed instance', async ({
	network,
	operatePreviewProcessInstancePage,
	page,
}) => {
	network.use(
		...processInstanceHeaderHandlers(
			createProcessInstance({
				processInstanceKey: PROCESS_INSTANCE_ID,
				state: 'COMPLETED',
				endDate: '2026-01-15T11:00:00.000Z',
			}),
		),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

	await expect(operatePreviewProcessInstancePage.header.getByText('My Process')).toBeVisible();
	await expect(page).toHaveScreenshot();
});

test('should match the header snapshot with the breadcrumb of a called instance', async ({
	network,
	operatePreviewProcessInstancePage,
	page,
}) => {
	network.use(
		mockGetProcessInstanceCallHierarchyEndpoint({
			successResponse: HttpResponse.json(callHierarchyOf(['Parent Process', 'Child Process'])),
		}),
		...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

	await expect(
		operatePreviewProcessInstancePage.breadcrumb.getByRole('link', {name: 'View Process Parent Process - Instance 1'}),
	).toBeVisible();
	await operatePreviewProcessInstancePage.diagramZoomResetButton.click();
	await expect(page).toHaveScreenshot();
});

test('should match the header snapshot with the overflow menu of a deep breadcrumb open', async ({
	network,
	operatePreviewProcessInstancePage,
	page,
}) => {
	network.use(
		mockGetProcessInstanceCallHierarchyEndpoint({
			successResponse: HttpResponse.json(
				callHierarchyOf([
					'Root Process',
					'Second Process',
					'Third Process',
					'Fourth Process',
					'Fifth Process',
					'Current Process',
				]),
			),
		}),
		...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await expect(operatePreviewProcessInstancePage.breadcrumb.getByRole('button', {name: 'More'})).toBeVisible();
	await operatePreviewProcessInstancePage.diagramZoomResetButton.click();
	await operatePreviewProcessInstancePage.breadcrumb.getByRole('button', {name: 'More'}).click();

	await expect(page.getByRole('menuitem', {name: 'Third Process'})).toBeVisible();
	await expect(page).toHaveScreenshot();
});

test('should match the header snapshot with the collapsed operations menu open', async ({
	network,
	operatePreviewProcessInstancePage,
	page,
}) => {
	network.use(
		...processInstanceHeaderHandlers(
			createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident: true}),
		),
	);
	await page.setViewportSize(NARROW_VIEWPORT);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await operatePreviewProcessInstancePage.header.getByRole('button', {name: 'Actions'}).click();

	await expect(page.getByRole('menuitem', {name: /Cancel/})).toBeVisible();
	await expect(page).toHaveScreenshot();
});
