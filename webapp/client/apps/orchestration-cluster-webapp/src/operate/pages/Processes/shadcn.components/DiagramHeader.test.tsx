/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createQueryProcessInstancesResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {DiagramHeader} from './DiagramHeader';
import type {ProcessDefinitionSelection} from './processDefinitionSelection';

const DEFINITION = createProcessDefinition({
	name: 'Order Process',
	processDefinitionId: 'order-process',
	version: 2,
	versionTag: 'v2.0',
});

function renderHeader(processDefinitionSelection: ProcessDefinitionSelection) {
	return renderWithRouter(() => <DiagramHeader processDefinitionSelection={processDefinitionSelection} />, {
		path: '/operate-preview/processes',
	});
}

describe('<DiagramHeader /> (design system)', () => {
	it.beforeEach(({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			}),
			mockQueryProcessInstancesEndpoint({successResponse: HttpResponse.json(createQueryProcessInstancesResponse())}),
		);
	});

	it('should show a generic title when no process is selected', async () => {
		const screen = await renderHeader({kind: 'no-match'});

		await expect.element(screen.getByText('Process', {exact: true})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: /Delete Process Definition/})).not.toBeInTheDocument();
	});

	it('should show name, ID, version tag and delete action for a single version', async () => {
		const screen = await renderHeader({kind: 'single-version', definition: DEFINITION});

		await expect.element(screen.getByText('Order Process')).toBeVisible();
		await expect.element(screen.getByText('order-process')).toBeVisible();
		await expect.element(screen.getByText('v2.0')).toBeVisible();
		await expect
			.element(screen.getByRole('heading', {name: 'Order Process'}))
			.toHaveAttribute('title', 'Process name: Order Process');
		await expect
			.element(screen.getByRole('button', {name: /order-process/}))
			.toHaveAttribute('title', 'Copy Process ID: order-process');
		await expect
			.element(screen.getByRole('button', {name: 'Delete Process Definition "Order Process - Version 2"'}))
			.toBeVisible();
	});

	it('should hide the delete action when all versions are selected', async () => {
		const screen = await renderHeader({
			kind: 'all-versions',
			definition: {name: 'Order Process', processDefinitionId: 'order-process'},
		});

		await expect.element(screen.getByText('Order Process')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: /Delete Process Definition/})).not.toBeInTheDocument();
	});
});
