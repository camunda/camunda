/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {useRouterState} from '@tanstack/react-router';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetElementInstanceEndpoint,
	mockGetVariableEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryVariablesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {ProcessInstanceVariables} from './ProcessInstanceVariables';
import {processInstanceSearchSchema} from './processInstanceSearch';

const PROCESS_INSTANCE_ID = '2251799813685280';

afterEach(async () => {
	await cleanup();
	notificationsStore.reset();
});

function variablesResponse(
	items: Variable[],
	page?: {totalItems?: number; hasMoreTotalItems?: boolean; endCursor?: string},
) {
	return HttpResponse.json(createQueryVariablesByUserTaskResponse({items, page}));
}

function renderVariables(search = '') {
	function Variables() {
		const routeSearch = useRouterState({select: (state) => state.location.search});
		return (
			<div style={{height: '400px', display: 'flex', flexDirection: 'column'}}>
				<ProcessInstancePageProvider
					processInstanceId={PROCESS_INSTANCE_ID}
					processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})}
					search={processInstanceSearchSchema.parse(routeSearch)}
				>
					<ProcessInstanceVariables />
				</ProcessInstancePageProvider>
			</div>
		);
	}

	return renderWithRouter(Variables, {
		path: '/operate/processes/$processInstanceId/variables',
		initialEntry: `/operate/processes/${PROCESS_INSTANCE_ID}/variables${search}`,
	});
}

describe('<ProcessInstanceVariables />', () => {
	it('should show the process instance variables sorted by name', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				schema: z.object({
					filter: z
						.object({processInstanceKey: z.literal(PROCESS_INSTANCE_ID), scopeKey: z.literal(PROCESS_INSTANCE_ID)})
						.strict(),
					page: z.object({limit: z.literal(50)}).strict(),
					sort: z.tuple([z.object({field: z.literal('name'), order: z.literal('ASC')})]),
				}),
				successResponse: variablesResponse([
					createVariable({name: 'amount', value: '100', variableKey: '1'}),
					createVariable({name: 'customer', value: '{"name":"Jane"}', variableKey: '2'}),
				]),
				failureResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderVariables();

		await expect.element(screen.getByRole('button', {name: 'Copy amount'})).toHaveTextContent('100');
		await expect.element(screen.getByRole('button', {name: 'Copy customer'})).toHaveTextContent('{ "name": "Jane" }');
	});

	it('should show an empty message when the scope has no variables', async ({worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([])}));
		const screen = await renderVariables();

		await expect.element(screen.getByText('The element has no variables')).toBeVisible();
		await expect.element(screen.getByRole('searchbox')).not.toBeInTheDocument();
	});

	it('should show a permission message when variables are forbidden', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
			}),
		);
		const screen = await renderVariables();

		await expect.element(screen.getByText('Missing permissions to access Variables')).toBeVisible();
	});

	it('should show an error message when variables cannot be fetched', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderVariables();

		await expect.element(screen.getByText('Variables could not be fetched')).toBeVisible();
		await expect.element(screen.getByText('Refresh the page to try again')).toBeVisible();
	});

	it('should ask for a single instance when the selected element has several', async ({worker}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([], 3)),
			}),
		);
		const screen = await renderVariables('?elementId=user-task');

		await expect
			.element(screen.getByText('To view the variables, select a single element instance in the instance history.'))
			.toBeVisible();
	});

	it('should ask for a single instance when the selected element count is only a lower bound', async ({worker}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [createElementInstance()],
						page: {totalItems: 1, hasMoreTotalItems: true, startCursor: null, endCursor: null},
					}),
				),
			}),
		);
		const screen = await renderVariables('?elementId=user-task');

		await expect
			.element(screen.getByText('To view the variables, select a single element instance in the instance history.'))
			.toBeVisible();
	});

	it('should show an empty message for an element that has not run', async ({worker}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([], 0)),
			}),
		);
		const screen = await renderVariables('?elementId=user-task');

		await expect.element(screen.getByText('The element has no variables')).toBeVisible();
	});

	it('should show an empty message for a placeholder instance', async () => {
		const screen = await renderVariables('?elementId=user-task&elementInstanceKey=1&isPlaceholder=true');

		await expect.element(screen.getByText('The element has no variables')).toBeVisible();
	});

	it('should scope variables to the selected element instance', async ({worker}) => {
		const elementInstance = createElementInstance({elementInstanceKey: '2251799813685299'});
		worker.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
			mockQueryVariablesEndpoint({
				schema: z.object({filter: z.object({scopeKey: z.literal(elementInstance.elementInstanceKey)})}),
				successResponse: variablesResponse([createVariable({name: 'local'})]),
				failureResponse: variablesResponse([]),
			}),
		);
		const screen = await renderVariables(
			`?elementId=user-task&elementInstanceKey=${elementInstance.elementInstanceKey}`,
		);

		await expect.element(screen.getByTestId('variable-local')).toBeVisible();
	});

	it('should search variables by name', async ({worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}));
		const screen = await renderVariables();
		await expect.element(screen.getByTestId('variable-amount')).toBeVisible();

		worker.use(
			mockQueryVariablesEndpoint({
				schema: z.object({filter: z.object({name: z.object({$like: z.literal('*cust*')})})}),
				successResponse: variablesResponse([]),
				failureResponse: variablesResponse([createVariable({name: 'amount'})]),
			}),
		);
		await userEvent.fill(screen.getByRole('searchbox', {name: 'Search variables by name'}), ' cust ');

		await expect.element(screen.getByText('No variables match your search')).toBeVisible();

		await expect.element(screen.getByRole('searchbox', {name: 'Search variables by name'})).toBeVisible();
	});

	it('should show an error when searching variables fails', async ({worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}));
		const screen = await renderVariables();
		await expect.element(screen.getByTestId('variable-amount')).toBeVisible();

		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		await userEvent.fill(screen.getByRole('searchbox', {name: 'Search variables by name'}), 'cust');

		await expect.element(screen.getByText('Variables could not be fetched')).toBeVisible();
		await expect.element(screen.getByText('No variables match your search')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('searchbox', {name: 'Search variables by name'})).toBeVisible();
	});

	it('should show and copy the full value of a truncated variable', async ({worker}) => {
		vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: variablesResponse([
					createVariable({name: 'payload', value: '"abc', isTruncated: true, variableKey: '7'}),
				]),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createVariable({name: 'payload', value: '"abcdef"', variableKey: '7'})),
			}),
		);
		const screen = await renderVariables();

		await userEvent.click(screen.getByRole('button', {name: 'Show all'}));

		const dialog = screen.getByRole('dialog', {name: 'Full value of payload'});
		await expect.element(dialog).toBeVisible();
		await userEvent.click(dialog.getByRole('button', {name: 'Close'}).first());
		await expect.element(dialog).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Copy payload'}));

		await expect.poll(() => vi.mocked(navigator.clipboard.writeText).mock.calls).toEqual([['"abcdef"']]);
	});

	it('should open the full value of a variable', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: variablesResponse([createVariable({name: 'amount', value: '100', variableKey: '9'})]),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createVariable({name: 'amount', value: '100', variableKey: '9'})),
			}),
		);
		const screen = await renderVariables();

		await userEvent.click(screen.getByRole('button', {name: 'Open'}));

		const dialog = screen.getByRole('dialog', {name: 'Full value of amount'});
		await expect.element(dialog).toBeVisible();
		await userEvent.click(dialog.getByRole('button', {name: 'Close'}).first());
		await expect.element(dialog).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Open'})).toHaveFocus();
	});

	it('should notify when the full value cannot be fetched', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: variablesResponse([createVariable({name: 'amount', variableKey: '9'})]),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderVariables();

		await userEvent.click(screen.getByRole('button', {name: 'Open'}));

		await expect
			.poll(() => notificationsStore.notifications.map(({title}) => title))
			.toContain('Failed to fetch full amount');
		await expect.element(screen.getByRole('button', {name: 'Open'})).toHaveFocus();
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
	});
});
