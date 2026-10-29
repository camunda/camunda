/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {userEvent} from 'vitest/browser';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {HttpResponse, http} from 'msw';
import {endpoints} from '#/shared/http/endpoints';
import {it} from '#/vitest-modules/test-extend';
import {mockCreateDecisionInstancesDeletionBatchOperationEndpoint} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Button, Toaster, toast} from '@camunda/design-system';
import {Notifications} from '#/shared/notifications/shadcn.components/Notifications';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {BulkDeleteDialog} from './BulkDeleteDialog';
import {useBulkDelete} from './useBulkDelete';

const FILTER = {state: {$in: ['EVALUATED', 'FAILED']}} as never;

type HarnessProps = Parameters<typeof useBulkDelete>[0];

const Harness: React.FC<HarnessProps> = (props) => {
	const {batchActions, selectionCountLabel, dialogProps} = useBulkDelete(props);

	return (
		<>
			<span>{selectionCountLabel}</span>
			{batchActions.map(({id, label, onClick}) => (
				<Button key={id} onClick={onClick}>
					{label}
				</Button>
			))}
			<BulkDeleteDialog {...dialogProps} />
		</>
	);
};

async function renderHarness(props: Partial<HarnessProps> = {}, basepath = '') {
	const onDeleted = vi.fn();
	const onDiscard = vi.fn();
	const screen = await renderWithRouter(
		() => (
			<>
				<Harness
					selectedCount={2}
					includedIds={['1', '2']}
					excludedIds={[]}
					filter={FILTER}
					onDeleted={onDeleted}
					onDiscard={onDiscard}
					{...props}
				/>
				<Toaster />
				<Notifications />
			</>
		),
		{path: '/operate-preview/decisions', basepath},
	);
	return {screen, onDeleted, onDiscard};
}

describe('useBulkDelete (shadcn)', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
		notificationsStore.reset();
		toast.dismiss();
	});

	it('should label the selection count', async () => {
		const {screen} = await renderHarness();

		await expect.element(screen.getByText('2 items selected')).toBeVisible();
	});

	it('should use the singular label for a single selection', async () => {
		const {screen} = await renderHarness({selectedCount: 1, includedIds: ['1']});

		await expect.element(screen.getByText('1 item selected')).toBeVisible();
	});

	it('should start the batch delete after confirming and notify', async ({worker}) => {
		worker.use(
			mockCreateDecisionInstancesDeletionBatchOperationEndpoint({
				successResponse: HttpResponse.json({
					batchOperationKey: 'batch-op-1',
					batchOperationType: 'DELETE_DECISION_INSTANCE',
				}),
			}),
		);
		const {screen, onDeleted} = await renderHarness();

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));
		await expect.element(screen.getByRole('alertdialog')).toBeVisible();
		await expect.element(screen.getByText(/2 instances selected for delete operation/)).toBeVisible();
		await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Delete'}));

		await expect
			.element(screen.getByText('The batch operation "Delete Decision Instance" has been started'))
			.toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Go to operation details'})).toBeVisible();
		expect(onDeleted).toHaveBeenCalledOnce();
	});

	it('should close the confirmation when the selection is cleared', async () => {
		const ClearableHarness: React.FC = () => {
			const [selectedCount, setSelectedCount] = useState(2);
			return (
				<>
					<Harness
						selectedCount={selectedCount}
						includedIds={[]}
						excludedIds={[]}
						filter={FILTER}
						onDeleted={vi.fn()}
						onDiscard={vi.fn()}
					/>
					<button type="button" onClick={() => setSelectedCount(0)}>
						Clear selection
					</button>
				</>
			);
		};
		const screen = await renderWithRouter(() => <ClearableHarness />, {path: '/operate-preview/decisions'});

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));
		await expect.element(screen.getByRole('alertdialog')).toBeVisible();
		(screen.getByText('Clear selection').element() as HTMLElement).click();

		await expect.element(screen.getByRole('alertdialog')).not.toBeInTheDocument();
	});

	it.for([
		{basepath: '', expectedPath: '/operate/batch-operations/batch-op-1'},
		{basepath: '/camunda', expectedPath: '/camunda/operate/batch-operations/batch-op-1'},
	])(
		'should open the batch operation details with the basepath "$basepath"',
		async ({basepath, expectedPath}, {worker}) => {
			worker.use(
				mockCreateDecisionInstancesDeletionBatchOperationEndpoint({
					successResponse: HttpResponse.json({
						batchOperationKey: 'batch-op-1',
						batchOperationType: 'DELETE_DECISION_INSTANCE',
					}),
				}),
			);
			const {screen} = await renderHarness({}, basepath);

			await userEvent.click(screen.getByRole('button', {name: 'Delete'}));
			await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Delete'}));
			await userEvent.click(screen.getByRole('button', {name: 'Go to operation details'}));

			await expect.poll(() => screen.router.history.location.pathname).toBe(expectedPath);
		},
	);

	it.for([
		{
			name: 'individual selection',
			props: {includedIds: ['1', '2'], excludedIds: []},
			filter: FILTER,
			key: {$in: ['1', '2']},
		},
		{
			name: 'select-all with exclusions',
			props: {includedIds: [], excludedIds: ['3']},
			filter: FILTER,
			key: {$notIn: ['3']},
		},
		{
			name: 'select-all with exclusions under an active instance-key filter',
			props: {includedIds: [], excludedIds: ['1']},
			filter: {state: {$in: ['EVALUATED']}, decisionEvaluationInstanceKey: {$in: ['1', '2', '3']}} as never,
			key: {$in: ['1', '2', '3'], $notIn: ['1']},
		},
		{
			name: 'an individual selection under an active instance-key filter',
			props: {includedIds: ['2', '9'], excludedIds: []},
			filter: {state: {$in: ['EVALUATED']}, decisionEvaluationInstanceKey: {$in: ['1', '2', '3']}} as never,
			key: {$in: ['2']},
		},
	])('should send the expected filter for $name', async ({props, filter, key}, {worker}) => {
		let body: {filter: Record<string, unknown>} | undefined;
		worker.use(
			http.post(endpoints.createDecisionInstancesDeletionBatchOperation({filter: {}}).url, async ({request}) => {
				body = (await request.json()) as typeof body;
				return HttpResponse.json({
					batchOperationKey: 'batch-op-1',
					batchOperationType: 'DELETE_DECISION_INSTANCE',
				});
			}),
		);
		const {screen, onDeleted} = await renderHarness({...props, filter});

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));
		await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Delete'}));

		await expect.poll(() => onDeleted.mock.calls.length).toBe(1);
		expect(body?.filter.decisionEvaluationInstanceKey).toEqual(key);
	});

	it('should close the dialog, discard the selection and not delete when cancelling', async () => {
		const {screen, onDeleted, onDiscard} = await renderHarness();

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));
		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));

		await expect.element(screen.getByRole('alertdialog')).not.toBeInTheDocument();
		expect(onDeleted).not.toHaveBeenCalled();
		expect(onDiscard).toHaveBeenCalledOnce();
	});

	it('should show a permission warning when the delete request is forbidden', async ({worker}) => {
		worker.use(
			mockCreateDecisionInstancesDeletionBatchOperationEndpoint({
				successResponse: new HttpResponse(null, {status: 403}),
			}),
		);
		const {screen, onDeleted} = await renderHarness();

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));
		await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Delete'}));

		await expect.element(screen.getByText("You don't have permission to perform this operation")).toBeVisible();
		expect(onDeleted).not.toHaveBeenCalled();
	});
});
