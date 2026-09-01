/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockQueryProcessInstancesEndpoint} from '#/shared-test-modules/mock-handlers';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {InstancesTable} from './InstancesTable';
import type {ProcessesSearch} from './processesFilter';

const BASE_SEARCH: ProcessesSearch = {active: true, incidents: true, completed: false, canceled: false};

function renderInstancesTable(search: ProcessesSearch = BASE_SEARCH) {
	return renderWithRouter(
		() => (
			// The table's scroll container is `height: 100%` and needs a sized ancestor, which the
			// full page's Frame/ResizablePanel normally provides — give it one here in isolation.
			<div style={{height: '100vh'}}>
				<InstancesTable search={search} />
			</div>
		),
		{path: '/operate/processes'},
	);
}

describe('<InstancesTable />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render process instance rows', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [
							createProcessInstance({processInstanceKey: '1', processDefinitionName: 'Order Process'}),
							createProcessInstance({processInstanceKey: '2', processDefinitionName: 'Payment Process'}),
						],
					}),
				),
			}),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('Order Process')).toBeVisible();
		await expect.element(screen.getByText('Payment Process')).toBeVisible();
	});

	it('should link each instance key to its process instance page', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({items: [createProcessInstance({processInstanceKey: '42'})]}),
				),
			}),
		);

		const screen = await renderInstancesTable();

		await expect
			.element(screen.getByRole('link', {name: 'View instance 42'}))
			.toHaveAttribute('href', '/operate/processes/42');
	});

	it('should show the version tag and business ID columns only when a row has one', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({items: [createProcessInstance({processInstanceKey: '1'})]}),
				),
			}),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByRole('columnheader', {name: 'Name'})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: 'Version Tag'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('columnheader', {name: 'Business ID'})).not.toBeInTheDocument();
	});

	it('should show the version tag and business ID columns when a row has them', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [
							createProcessInstance({
								processInstanceKey: '1',
								processDefinitionVersionTag: 'v1.2',
								businessId: 'order-1',
							}),
						],
					}),
				),
			}),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('v1.2')).toBeVisible();
		await expect.element(screen.getByText('order-1')).toBeVisible();
	});

	it('should render an incident icon for an instance with an incident', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '1', state: 'ACTIVE', hasIncident: true})],
					}),
				),
			}),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByTestId('INCIDENT-icon')).toBeVisible();
	});

	it('should show the empty message with a hint when no instance state is selected', async () => {
		const screen = await renderInstancesTable({
			active: false,
			incidents: false,
			completed: false,
			canceled: false,
		});

		await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
		await expect.element(screen.getByText('To see some results, select at least one Instance state')).toBeVisible();
	});

	it('should show the parent instance link only when the instance has a parent', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [
							createProcessInstance({processInstanceKey: '1', parentProcessInstanceKey: '99'}),
							createProcessInstance({processInstanceKey: '2', parentProcessInstanceKey: null}),
						],
					}),
				),
			}),
		);

		const screen = await renderInstancesTable();

		await expect
			.element(screen.getByRole('link', {name: 'View parent instance 99'}))
			.toHaveAttribute('href', '/operate/processes/99');
		await expect.element(screen.getByText('None')).toBeVisible();
	});
});
