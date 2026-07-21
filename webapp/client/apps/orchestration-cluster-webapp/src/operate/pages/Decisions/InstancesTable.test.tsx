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
import {
	mockCreateDecisionInstancesDeletionBatchOperationEndpoint,
	mockQueryDecisionInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {InstancesTable} from './InstancesTable';
import type {DecisionsSearch} from './decisionsFilter';

const BASE_SEARCH: DecisionsSearch = {evaluated: true, failed: true};

function renderInstancesTable(search: DecisionsSearch = BASE_SEARCH) {
	return renderWithRouter(
		() => (
			// The table's scroll container is `height: 100%` and needs a sized ancestor, which the
			// full page's Frame/ResizablePanel normally provides — give it one here in isolation.
			<div style={{height: '100vh'}}>
				<InstancesTable search={search} />
				<Notifications />
			</div>
		),
		{path: '/operate/decisions'},
	);
}

describe('<InstancesTable />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
		notificationsStore.reset();
	});

	it('should render decision instance rows', async ({worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [
							createDecisionInstance({decisionEvaluationInstanceKey: '1', decisionDefinitionName: 'Invoice Approval'}),
							createDecisionInstance({decisionEvaluationInstanceKey: '2', decisionDefinitionName: 'Discount Rate'}),
						],
					}),
				),
			}),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('Invoice Approval')).toBeVisible();
		await expect.element(screen.getByText('Discount Rate')).toBeVisible();
	});

	it('should show the business ID column only when at least one row has one', async ({worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [createDecisionInstance({decisionEvaluationInstanceKey: '1', businessId: 'order-1'})],
					}),
				),
			}),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('order-1')).toBeVisible();
	});

	it('should render an empty message with no instance-state checkbox selected', async ({worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({successResponse: HttpResponse.json(createQueryDecisionInstancesResponse())}),
		);

		const screen = await renderInstancesTable({evaluated: false, failed: false});

		await expect.element(screen.getByText('To see some results, select at least one Instance state')).toBeVisible();
	});

	it('should render an empty message when no instances match the filter', async ({worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({successResponse: HttpResponse.json(createQueryDecisionInstancesResponse())}),
		);

		const screen = await renderInstancesTable();

		await expect.element(screen.getByText('There are no Instances matching this filter set')).toBeVisible();
	});

	describe('selection and delete', () => {
		it('should show the toolbar once a row is selected, and hide it after discarding', async ({worker}) => {
			worker.use(
				mockQueryDecisionInstancesEndpoint({
					successResponse: HttpResponse.json(
						createQueryDecisionInstancesResponse({
							items: [createDecisionInstance({decisionEvaluationInstanceKey: '1'})],
						}),
					),
				}),
			);

			const screen = await renderInstancesTable();

			await expect.element(screen.getByRole('button', {name: 'Delete'})).not.toBeInTheDocument();

			// force: Carbon's checkbox label visually covers the native input, failing real-browser actionability
			await screen.getByRole('checkbox', {name: 'Select row 1'}).click({force: true});

			await expect.element(screen.getByRole('button', {name: 'Delete'})).toBeVisible();
			await expect.element(screen.getByText('1 item selected')).toBeVisible();

			await screen.getByRole('button', {name: 'Discard'}).click();

			await expect.element(screen.getByRole('button', {name: 'Delete'})).not.toBeInTheDocument();
		});

		it('should delete the selected instance and show a success notification', async ({worker}) => {
			worker.use(
				mockQueryDecisionInstancesEndpoint({
					successResponse: HttpResponse.json(
						createQueryDecisionInstancesResponse({
							items: [createDecisionInstance({decisionEvaluationInstanceKey: '1'})],
						}),
					),
				}),
				mockCreateDecisionInstancesDeletionBatchOperationEndpoint({
					successResponse: HttpResponse.json({
						batchOperationKey: 'batch-op-1',
						batchOperationType: 'DELETE_DECISION_INSTANCE',
					}),
				}),
			);

			const screen = await renderInstancesTable();

			// force: Carbon's checkbox label visually covers the native input, failing real-browser actionability
			await screen.getByRole('checkbox', {name: 'Select row 1'}).click({force: true});
			await screen.getByRole('button', {name: 'Delete'}).click();
			await screen.getByRole('button', {name: 'Delete'}).last().click();

			await expect
				.element(screen.getByText('The batch operation "Delete decision instance" has been started'))
				.toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Delete'})).not.toBeInTheDocument();
		});

		it('should show a permission warning when the delete request is forbidden', async ({worker}) => {
			worker.use(
				mockQueryDecisionInstancesEndpoint({
					successResponse: HttpResponse.json(
						createQueryDecisionInstancesResponse({
							items: [createDecisionInstance({decisionEvaluationInstanceKey: '1'})],
						}),
					),
				}),
				mockCreateDecisionInstancesDeletionBatchOperationEndpoint({
					successResponse: new HttpResponse(null, {status: 403}),
				}),
			);

			const screen = await renderInstancesTable();

			// force: Carbon's checkbox label visually covers the native input, failing real-browser actionability
			await screen.getByRole('checkbox', {name: 'Select row 1'}).click({force: true});
			await screen.getByRole('button', {name: 'Delete'}).click();
			await screen.getByRole('button', {name: 'Delete'}).last().click();

			await expect.element(screen.getByText("You don't have permission to perform this operation")).toBeVisible();
		});
	});
});
