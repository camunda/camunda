/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, onTestFinished, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import type {SetupWorker} from 'msw/browser';
import {useRouterState} from '@tanstack/react-router';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {setUpFakeIntersectionObserver} from '#/vitest-modules/fake-intersection-observer';
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
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {ProcessInstanceVariables} from './ProcessInstanceVariables';
import {processInstanceSearchSchema} from './processInstanceSearch';

const PROCESS_INSTANCE_ID = '2251799813685280';

afterEach(async () => {
	await cleanup();
});

function variablesResponse(items: Variable[], totalItems = items.length) {
	return HttpResponse.json(createQueryVariablesByUserTaskResponse({items, page: {totalItems}}));
}

function trackVariableSearches(worker: SetupWorker) {
	const bodies: unknown[] = [];
	const onRequest = async ({request}: {request: Request}) => {
		if (request.url.endsWith('/variables/search')) {
			bodies.push(await request.clone().json());
		}
	};
	worker.events.on('request:start', onRequest);
	onTestFinished(() => worker.events.removeListener('request:start', onRequest));
	return bodies;
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
		const bodies = trackVariableSearches(worker);
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: variablesResponse([
					createVariable({name: 'amount', value: '100', variableKey: '1'}),
					createVariable({name: 'customer', value: '{"name":"Jane"}', variableKey: '2'}),
				]),
				delay: 100,
			}),
		);
		const screen = await renderVariables();

		await expect.element(screen.getByTestId('variables-spinner')).toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Copy amount'})).toHaveTextContent('100');
		await expect.element(screen.getByRole('button', {name: 'Copy customer'})).toHaveTextContent('{ "name": "Jane" }');
		expect(screen.getByTestId('variables-spinner').query()).toBeNull();
		await expect
			.poll(() => bodies)
			.toEqual([
				{
					filter: {processInstanceKey: PROCESS_INSTANCE_ID, scopeKey: PROCESS_INSTANCE_ID},
					page: {from: 0, limit: 50},
					sort: [{field: 'name', order: 'ASC'}],
				},
			]);
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
		const bodies = trackVariableSearches(worker);
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([], 3)),
			}),
		);
		const screen = await renderVariables('?elementId=user-task');

		await expect
			.element(screen.getByText('To view the variables, select a single element instance in the instance history.'))
			.toBeVisible();
		expect(bodies).toEqual([]);
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

	it('should not query variables for a placeholder instance', async ({worker}) => {
		const bodies = trackVariableSearches(worker);
		const screen = await renderVariables('?elementId=user-task&elementInstanceKey=1&isPlaceholder=true');

		await expect.element(screen.getByText('The element has no variables')).toBeVisible();
		expect(bodies).toEqual([]);
	});

	it('should scope variables to the selected element instance', async ({worker}) => {
		const bodies = trackVariableSearches(worker);
		const elementInstance = createElementInstance({elementInstanceKey: '2251799813685299'});
		worker.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
			mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'local'})])}),
		);
		const screen = await renderVariables(
			`?elementId=user-task&elementInstanceKey=${elementInstance.elementInstanceKey}`,
		);

		await expect.element(screen.getByTestId('variable-local')).toBeVisible();
		await expect
			.poll(() => bodies)
			.toContainEqual(
				expect.objectContaining({
					filter: {processInstanceKey: PROCESS_INSTANCE_ID, scopeKey: elementInstance.elementInstanceKey},
				}),
			);
	});

	it('should search variables by name', async ({worker}) => {
		const bodies = trackVariableSearches(worker);
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}));
		const screen = await renderVariables();
		await expect.element(screen.getByTestId('variable-amount')).toBeVisible();

		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([])}));
		await userEvent.fill(screen.getByRole('searchbox', {name: 'Search variables by name'}), ' cust ');

		await expect.element(screen.getByText('No variables match your search')).toBeVisible();
		await expect
			.poll(() => bodies)
			.toContainEqual(
				expect.objectContaining({
					filter: {processInstanceKey: PROCESS_INSTANCE_ID, scopeKey: PROCESS_INSTANCE_ID, name: {$like: '*cust*'}},
				}),
			);
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
		await expect.poll(() => dialog.element().textContent, {timeout: 10000}).toContain('"abcdef"');
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

		await expect.element(screen.getByRole('dialog', {name: 'Full value of amount'})).toBeVisible();
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

		await expect.element(screen.getByRole('button', {name: 'Open'})).toBeVisible();
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
	});

	describe('pagination', () => {
		const {getObserver} = setUpFakeIntersectionObserver();

		it('should load the next page when scrolling to the end', async ({worker}) => {
			const bodies = trackVariableSearches(worker);
			const firstPage = Array.from({length: 50}, (_, index) =>
				createVariable({name: `var-${String(index).padStart(2, '0')}`, variableKey: `${index}`}),
			);
			worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse(firstPage, 51)}));
			const screen = await renderVariables();
			await expect.element(screen.getByTestId('variable-var-49')).toBeInTheDocument();

			worker.use(
				mockQueryVariablesEndpoint({
					successResponse: variablesResponse([createVariable({name: 'var-50', variableKey: '50'})], 51),
				}),
			);
			const list = screen.getByTestId('variables-list').element();
			Object.defineProperty(list, 'scrollTop', {value: 100, configurable: true});
			getObserver().intersect(screen.getByTestId('variable-var-49').element());

			await expect.element(screen.getByTestId('variable-var-50')).toBeInTheDocument();
			await expect.poll(() => bodies).toContainEqual(expect.objectContaining({page: {from: 50, limit: 50}}));
		});
	});
});
