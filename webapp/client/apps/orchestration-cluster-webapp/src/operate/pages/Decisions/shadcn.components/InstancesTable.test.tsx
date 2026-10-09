/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {Toaster, toast} from '@camunda/design-system';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCreateDecisionInstancesDeletionBatchOperationEndpoint,
	mockQueryDecisionInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Notifications} from '#/shared/notifications/shadcn.components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {InstancesTable} from './InstancesTable';
import type {DecisionsSearch} from '../decisionsFilter';

const BASE_SEARCH: DecisionsSearch = {evaluated: true, failed: true};
const DECISIONS_LIST_PATH = '/operate/decisions';

const SizedContainer: React.FC<{children: React.ReactNode}> = ({children}) => (
	<div style={{height: '100vh'}}>
		{children}
		<Toaster />
		<Notifications />
	</div>
);

function renderInstancesTable({
	search = BASE_SEARCH,
	basepath = '',
}: {
	search?: DecisionsSearch;
	basepath?: string;
} = {}) {
	return renderWithRouter(
		() => (
			<SizedContainer>
				<InstancesTable search={search} />
			</SizedContainer>
		),
		{
			path: DECISIONS_LIST_PATH,
			basepath,
			initialEntry: `${basepath}${DECISIONS_LIST_PATH}`,
		},
	);
}

function mockInstances(items: ReturnType<typeof createDecisionInstance>[]) {
	return mockQueryDecisionInstancesEndpoint({
		successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items})),
	});
}

describe('<InstancesTable /> (shadcn)', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
		notificationsStore.reset();
		toast.dismiss();
	});

	it('should render decision instance rows with a state icon and key link', async ({worker}) => {
		worker.use(
			mockInstances([
				createDecisionInstance({
					decisionEvaluationInstanceKey: '1',
					decisionDefinitionName: 'Invoice Approval',
					state: 'EVALUATED',
				}),
				createDecisionInstance({
					decisionEvaluationInstanceKey: '2',
					decisionDefinitionName: 'Discount Rate',
					state: 'FAILED',
				}),
			]),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('Invoice Approval')).toBeVisible();
		await expect.element(screen.getByText('Discount Rate')).toBeVisible();
		await expect.element(screen.getByTestId('EVALUATED-icon-1')).toBeInTheDocument();
		await expect.element(screen.getByTestId('FAILED-icon-2')).toBeInTheDocument();
		await expect.element(screen.getByRole('link', {name: '1'})).toHaveAttribute('href', '/operate/decisions/1');
	});

	it('should show the business ID column only when at least one row has one', async ({worker}) => {
		worker.use(mockInstances([createDecisionInstance({decisionEvaluationInstanceKey: '1', businessId: 'order-1'})]));

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('order-1')).toBeVisible();
	});

	it('should not show the business ID column when no row has one', async ({worker}) => {
		worker.use(mockInstances([createDecisionInstance({decisionEvaluationInstanceKey: '1', businessId: undefined})]));

		const screen = await renderInstancesTable();

		await expect.element(screen.getByRole('button', {name: 'Evaluation date'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Business ID'})).not.toBeInTheDocument();
	});

	it('should show the tenant column only when multi-tenancy is enabled without a specific tenant', async ({worker}) => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(
				createSystemConfiguration({
					deployment: {isMultiTenancyEnabled: true, isTenantsApiEnabled: false, maxRequestSize: 0},
				}),
			),
		);
		worker.use(mockInstances([createDecisionInstance({decisionEvaluationInstanceKey: '1', tenantId: 'tenant-a'})]));

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('Tenant', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('tenant-a')).toBeVisible();
	});

	it('should render an empty message with no instance-state checkbox selected', async ({worker}) => {
		worker.use(mockInstances([]));

		const screen = await renderInstancesTable({search: {evaluated: false, failed: false}});

		await expect.element(screen.getByText('To see some results, select at least one instance state')).toBeVisible();
	});

	it('should render an empty message when no instances match the filter', async ({worker}) => {
		worker.use(mockInstances([]));

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('There are no instances matching this filter set')).toBeVisible();
	});

	it('should render an error message when the request fails', async ({worker}) => {
		worker.use(mockQueryDecisionInstancesEndpoint({successResponse: new HttpResponse(null, {status: 500})}));

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
	});

	describe('selection and delete', () => {
		it('should show the toolbar once a row is selected, and hide it after discarding', async ({worker}) => {
			worker.use(mockInstances([createDecisionInstance({decisionEvaluationInstanceKey: '1'})]));

			const screen = await renderInstancesTable();

			await expect.element(screen.getByRole('button', {name: 'Delete'})).not.toBeInTheDocument();

			await userEvent.click(screen.getByRole('checkbox', {name: 'Select row 1'}));

			await expect.element(screen.getByRole('button', {name: 'Delete'})).toBeVisible();
			await expect.element(screen.getByText('1 item selected')).toBeVisible();

			await userEvent.click(screen.getByRole('button', {name: 'Discard'}));

			await expect.element(screen.getByRole('button', {name: 'Delete'})).not.toBeInTheDocument();
		});

		it('should select all rows and report the total in the toolbar', async ({worker}) => {
			worker.use(
				mockInstances([
					createDecisionInstance({decisionEvaluationInstanceKey: '1'}),
					createDecisionInstance({decisionEvaluationInstanceKey: '2'}),
				]),
			);

			const screen = await renderInstancesTable();

			await userEvent.click(screen.getByRole('checkbox', {name: 'Select all rows'}));

			await expect.element(screen.getByText('2 items selected')).toBeVisible();
			await expect.element(screen.getByRole('checkbox', {name: 'Select row 1'})).toBeChecked();
			await expect.element(screen.getByRole('checkbox', {name: 'Select row 2'})).toBeChecked();
		});

		it('should clear the selection when the filter changes', async ({worker}) => {
			worker.use(mockInstances([createDecisionInstance({decisionEvaluationInstanceKey: '1'})]));

			const FilterSwitcher = () => {
				const [search, setSearch] = useState<DecisionsSearch>(BASE_SEARCH);
				return (
					<SizedContainer>
						<button type="button" onClick={() => setSearch({evaluated: true, failed: false})}>
							Change filter
						</button>
						<InstancesTable search={search} />
					</SizedContainer>
				);
			};
			const screen = await renderWithRouter(() => <FilterSwitcher />, {
				path: DECISIONS_LIST_PATH,
				initialEntry: DECISIONS_LIST_PATH,
			});

			await userEvent.click(screen.getByRole('checkbox', {name: 'Select row 1'}));
			await expect.element(screen.getByText('1 item selected')).toBeVisible();

			await userEvent.click(screen.getByRole('button', {name: 'Change filter'}));

			await expect.element(screen.getByText('1 item selected')).not.toBeInTheDocument();
		});

		it('should delete the selected instance and show a success notification', async ({worker}) => {
			worker.use(
				mockInstances([createDecisionInstance({decisionEvaluationInstanceKey: '1'})]),
				mockCreateDecisionInstancesDeletionBatchOperationEndpoint({
					successResponse: HttpResponse.json({
						batchOperationKey: 'batch-op-1',
						batchOperationType: 'DELETE_DECISION_INSTANCE',
					}),
				}),
			);

			const screen = await renderInstancesTable();

			await userEvent.click(screen.getByRole('checkbox', {name: 'Select row 1'}));
			await userEvent.click(screen.getByRole('button', {name: 'Delete'}));
			await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Delete'}));

			await expect
				.element(screen.getByText('The batch operation "Delete Decision Instance" has been started'))
				.toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Discard'})).not.toBeInTheDocument();
		});

		it('should show a permission warning when the delete request is forbidden', async ({worker}) => {
			worker.use(
				mockInstances([createDecisionInstance({decisionEvaluationInstanceKey: '1'})]),
				mockCreateDecisionInstancesDeletionBatchOperationEndpoint({
					successResponse: new HttpResponse(null, {status: 403}),
				}),
			);

			const screen = await renderInstancesTable();

			await userEvent.click(screen.getByRole('checkbox', {name: 'Select row 1'}));
			await userEvent.click(screen.getByRole('button', {name: 'Delete'}));
			await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Delete'}));

			await expect.element(screen.getByText("You don't have permission to perform this operation")).toBeVisible();
		});
	});

	it.for([
		{basepath: '', expectedPathPrefix: ''},
		{basepath: '/camunda', expectedPathPrefix: '/camunda'},
	])(
		'should render process instance links with the correct basepath "$basepath"',
		async ({basepath, expectedPathPrefix}, {worker}) => {
			worker.use(
				mockInstances([
					createDecisionInstance({decisionEvaluationInstanceKey: '1', processInstanceKey: '2251799813685250'}),
				]),
			);

			const screen = await renderInstancesTable({basepath});

			await expect
				.element(screen.getByRole('link', {name: 'View process instance 2251799813685250'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/processes/2251799813685250`);
		},
	);

	it.for([
		{basepath: '', expectedPathPrefix: ''},
		{basepath: '/camunda', expectedPathPrefix: '/camunda'},
	])(
		'should render decision instance links with the correct basepath "$basepath"',
		async ({basepath, expectedPathPrefix}, {worker}) => {
			worker.use(mockInstances([createDecisionInstance({decisionEvaluationInstanceKey: '1'})]));

			const screen = await renderInstancesTable({basepath});

			await expect
				.element(screen.getByRole('link', {name: '1'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/decisions/1`);
		},
	);

	it('should render "None" instead of a process instance link when the process instance key is missing', async ({
		worker,
	}) => {
		worker.use(
			mockInstances([createDecisionInstance({decisionEvaluationInstanceKey: '1', processInstanceKey: undefined})]),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('None')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: /view process instance/i})).not.toBeInTheDocument();
	});
});
