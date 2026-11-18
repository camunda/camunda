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
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createIncident} from '#/shared-test-modules/api-mocks/incidents';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';

const PROCESS_INSTANCE_ID = '2251799813685280';
const NARROW_VIEWPORT = {width: 1000, height: 800};

function incidentHandlers() {
	return [
		mockQueryProcessInstanceIncidentsEndpoint({
			successResponse: HttpResponse.json(
				createPaginatedResponse({
					items: [createIncident({processInstanceKey: PROCESS_INSTANCE_ID})],
					page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
				}),
			),
		}),
	];
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
	network.use(...incidentHandlers());

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

	await expect(operatePreviewProcessInstancePage.headerText('My Process')).toBeVisible();
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

	await expect(operatePreviewProcessInstancePage.headerText('My Process')).toBeVisible();
	await expect(page).toHaveScreenshot();
});

test('should match the header snapshot with the breadcrumb of a called instance', async ({
	network,
	operatePreviewProcessInstancePage,
	page,
}) => {
	network.use(...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})));
	network.use(
		mockGetProcessInstanceCallHierarchyEndpoint({
			successResponse: HttpResponse.json([
				createCallHierarchy({processInstanceKey: '1', processDefinitionName: 'Parent Process'}),
				createCallHierarchy({processInstanceKey: PROCESS_INSTANCE_ID, processDefinitionName: 'Child Process'}),
			]),
		}),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

	await expect(
		operatePreviewProcessInstancePage.breadcrumbLink('View Process Parent Process - Instance 1'),
	).toBeVisible();
	await expect(page).toHaveScreenshot();
});

test('should match the header snapshot with the overflow menu of a deep breadcrumb open', async ({
	network,
	operatePreviewProcessInstancePage,
	page,
}) => {
	network.use(...processInstanceHeaderHandlers(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})));
	network.use(
		mockGetProcessInstanceCallHierarchyEndpoint({
			successResponse: HttpResponse.json([
				createCallHierarchy({processInstanceKey: '1', processDefinitionName: 'Root Process'}),
				createCallHierarchy({processInstanceKey: '2', processDefinitionName: 'Second Process'}),
				createCallHierarchy({processInstanceKey: '3', processDefinitionName: 'Third Process'}),
				createCallHierarchy({processInstanceKey: '4', processDefinitionName: 'Fourth Process'}),
				createCallHierarchy({processInstanceKey: '5', processDefinitionName: 'Fifth Process'}),
				createCallHierarchy({processInstanceKey: PROCESS_INSTANCE_ID, processDefinitionName: 'Current Process'}),
			]),
		}),
	);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await operatePreviewProcessInstancePage.breadcrumbMoreButton.click();

	await expect(operatePreviewProcessInstancePage.menuItem('Third Process')).toBeVisible();
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
	network.use(...incidentHandlers());
	await page.setViewportSize(NARROW_VIEWPORT);

	await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);
	await operatePreviewProcessInstancePage.actionsButton.click();

	await expect(operatePreviewProcessInstancePage.menuItem(/Cancel/)).toBeVisible();
	await expect(page).toHaveScreenshot();
});
