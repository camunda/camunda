/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, beforeAll} from 'vitest';
import {userEvent, type Locator} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {useRouterState} from '@tanstack/react-router';
import type {ProcessInstance, Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {Toaster, toast} from '@camunda/design-system';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetElementInstanceEndpoint,
	mockGetVariableEndpoint,
	mockQueryVariablesEndpoint,
	mockUpdateElementInstanceVariablesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createElementInstance} from '#/shared-test-modules/api-mocks/element-instances';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {variableRow} from '#/shared-test-modules/variable-row';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {Notifications} from '#/shared/notifications/shadcn.components/Notifications';
import {ProcessInstancePageProvider} from '../ProcessInstancePageProvider';
import {ProcessInstanceVariables} from './ProcessInstanceVariables';
import {processInstanceSearchSchema} from '../processInstanceSearch';
import {useModificationController} from '../useProcessInstanceModification';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {preloadMonaco} from '#/vitest-modules/preload-monaco';

const PROCESS_INSTANCE_ID = '2251799813685280';
const ELEMENT_INSTANCE_KEY = '2251799813685299';

afterEach(async () => {
	await cleanup();
	notificationsStore.reset();
	toast.dismiss();
});

function variablesResponse(items: Variable[]) {
	return HttpResponse.json(createQueryVariablesByUserTaskResponse({items}));
}

function savedResponse() {
	return new HttpResponse(null, {status: 204});
}

function rejectedResponse() {
	return HttpResponse.json(createProblemDetails({status: 400}), {status: 400});
}

async function appendToEditor(target: Locator, text: string, container: Locator = target) {
	await userEvent.click(target);
	await expect.element(container.getByRole('textbox')).toHaveFocus();
	await userEvent.keyboard(`{End}${text}`);
}

async function appendToNewValue(group: Locator, text: string) {
	await expect.element(group.getByRole('textbox')).toBeInTheDocument();
	await appendToEditor(group, text);
}

async function expectNoNotifications(screen: Awaited<ReturnType<typeof renderVariables>>) {
	const region = screen.getByRole('region', {name: /^Notifications/});
	await expect.element(region).toBeInTheDocument();
	await expect.element(region.getByRole('button', {name: 'Dismiss'})).not.toBeInTheDocument();
}

function SelectElementInstanceButton() {
	const {selectElementInstance} = useProcessInstanceElementSelection();

	return (
		<button
			type="button"
			onClick={() =>
				selectElementInstance({elementId: 'task', elementInstanceKey: ELEMENT_INSTANCE_KEY, type: 'SERVICE_TASK'})
			}
		>
			Select element instance
		</button>
	);
}

function renderVariables({
	search = '',
	processInstance,
}: {search?: string; processInstance?: Partial<ProcessInstance>} = {}) {
	function Variables() {
		const routeSearch = useRouterState({select: (state) => state.location.search});
		const modification = useModificationController(PROCESS_INSTANCE_ID);

		return (
			<div style={{height: '400px', display: 'flex', flexDirection: 'column'}}>
				<ProcessInstancePageProvider
					processInstanceId={PROCESS_INSTANCE_ID}
					processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, ...processInstance})}
					search={processInstanceSearchSchema.parse(routeSearch)}
					modification={modification}
				>
					<SelectElementInstanceButton />
					<ProcessInstanceVariables />
				</ProcessInstancePageProvider>
				<Toaster />
				<Notifications />
			</div>
		);
	}

	return renderWithRouter(Variables, {
		path: '/operate-preview/processes/$processInstanceId/variables',
		initialEntry: `/operate-preview/processes/${PROCESS_INSTANCE_ID}/variables${search}`,
	});
}

describe('<ProcessInstanceVariables /> editing', () => {
	beforeAll(preloadMonaco, 120_000);

	it('should save an edited variable', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: variablesResponse([createVariable({name: 'amount', value: '100', variableKey: '1'})]),
			}),
			mockUpdateElementInstanceVariablesEndpoint({
				schema: z.strictObject({variables: z.strictObject({amount: z.literal(1001)}), local: z.literal(true)}),
				successResponse: savedResponse(),
				failureResponse: rejectedResponse(),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createVariable({name: 'amount', value: '1001', variableKey: '1'})),
			}),
		);
		const screen = await renderVariables();
		const row = variableRow(screen, 'amount');

		await userEvent.click(row.getByRole('button', {name: /^Edit variable /}));
		await expect.element(screen.getByRole('button', {name: 'Add Variable'})).toBeDisabled();
		await expect.element(row.getByRole('button', {name: 'Save'})).toBeDisabled();
		await appendToEditor(row.getByText('100', {exact: true}), '1', row);
		await userEvent.click(row.getByRole('button', {name: 'Save'}));

		await expect.element(screen.getByText('Variable updated')).toBeVisible();
		await expect.element(row.getByRole('button', {name: /^Edit variable /})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Add Variable'})).toBeEnabled();
	});

	it('should edit the listed value of a complete variable instead of a previously opened full value', async ({
		worker,
	}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: variablesResponse([createVariable({name: 'amount', value: '100', variableKey: '1'})]),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createVariable({name: 'amount', value: '50', variableKey: '1'})),
			}),
		);
		const screen = await renderVariables();
		const row = variableRow(screen, 'amount');
		await userEvent.click(row.getByRole('button', {name: 'Open'}));
		const dialog = screen.getByRole('dialog', {name: 'Full value of amount'});
		await expect.element(dialog).toBeVisible();
		await userEvent.click(dialog.getByRole('button', {name: 'Close'}).first());

		await userEvent.click(row.getByRole('button', {name: /^Edit variable /}));

		await expect.element(row.getByRole('group', {name: 'amount'})).toHaveTextContent('100');
	});

	it('should not save a pending edit when searching with Enter', async ({worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}));
		const screen = await renderVariables();
		const row = variableRow(screen, 'amount');
		await userEvent.click(row.getByRole('button', {name: /^Edit variable /}));
		await appendToEditor(row.getByText('100', {exact: true}), '1', row);

		await userEvent.type(screen.getByRole('searchbox', {name: 'Search variables by name'}), 'amount{Enter}');

		await expect.element(row.getByRole('button', {name: 'Save'})).toBeEnabled();
		await expectNoNotifications(screen);
	});

	it('should save the full value of an edited truncated variable', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: variablesResponse([
					createVariable({name: 'payload', value: '123', isTruncated: true, variableKey: '7'}),
				]),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createVariable({name: 'payload', value: '1234567', variableKey: '7'})),
			}),
			mockUpdateElementInstanceVariablesEndpoint({
				schema: z.strictObject({variables: z.strictObject({payload: z.literal(12345678)}), local: z.literal(true)}),
				successResponse: savedResponse(),
				failureResponse: rejectedResponse(),
			}),
		);
		const screen = await renderVariables();
		const row = variableRow(screen, 'payload');

		await userEvent.click(row.getByRole('button', {name: /^Edit variable /}));
		await expect.element(row.getByRole('group', {name: 'payload'})).toMatchTextContent('1234567');

		worker.use(
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createVariable({name: 'payload', value: '12345678', variableKey: '7'})),
			}),
		);
		await appendToEditor(row.getByText('1234567', {exact: true}), '8', row);
		await userEvent.click(row.getByRole('button', {name: 'Save'}));

		await expect.element(screen.getByText('Variable updated')).toBeVisible();
	});

	it('should add a new variable', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				schema: z.object({filter: z.object({name: z.literal('foo')}), page: z.never().optional()}),
				successResponse: variablesResponse([createVariable({name: 'foo', value: '123'})]),
				failureResponse: variablesResponse([createVariable({name: 'amount'})]),
			}),
			mockUpdateElementInstanceVariablesEndpoint({
				schema: z.strictObject({variables: z.strictObject({foo: z.literal(123)}), local: z.literal(true)}),
				successResponse: savedResponse(),
				failureResponse: rejectedResponse(),
			}),
		);
		const screen = await renderVariables();

		await userEvent.click(screen.getByRole('button', {name: 'Add Variable'}));
		await expect.element(screen.getByRole('button', {name: 'Add Variable'})).not.toBeInTheDocument();
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'foo');
		await appendToNewValue(screen.getByRole('group', {name: 'Value'}), '123');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect.element(screen.getByText('Variable added')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Add Variable'})).toBeEnabled();
		await expect.element(screen.getByRole('textbox', {name: 'Name'})).not.toBeInTheDocument();
	});

	it('should add a variable to a scope without variables', async ({worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([])}));
		const screen = await renderVariables();

		await expect.element(screen.getByText('This element has no variables')).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Add Variable'}));

		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveFocus();
		await expect.element(screen.getByText('This element has no variables')).not.toBeInTheDocument();
	});

	it.for([
		{name: 'a b', error: 'Name is invalid'},
		{name: 'amount', error: 'Name should be unique'},
	])('should not save a new variable when the name shows "$error"', async ({name, error}, {worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}));
		const screen = await renderVariables();

		await userEvent.click(screen.getByRole('button', {name: 'Add Variable'}));
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), name);
		await appendToNewValue(screen.getByRole('group', {name: 'Value'}), '1');

		await expect.element(screen.getByText(error)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Save'})).toBeDisabled();
	});

	it('should not save a new variable whose name exists outside the loaded variables', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				schema: z.object({filter: z.object({name: z.literal('foo')})}),
				successResponse: variablesResponse([createVariable({name: 'foo'})]),
				failureResponse: variablesResponse([createVariable({name: 'amount'})]),
			}),
		);
		const screen = await renderVariables();

		await userEvent.click(screen.getByRole('button', {name: 'Add Variable'}));
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'foo');
		await appendToNewValue(screen.getByRole('group', {name: 'Value'}), '1');
		await userEvent.click(screen.getByRole('button', {name: 'Save'}));

		await expect.element(screen.getByText('Name should be unique')).toBeVisible();
		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveValue('foo');
		await expectNoNotifications(screen);
	});

	it('should not save a new variable with a value that is not JSON', async ({worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}));
		const screen = await renderVariables();

		await userEvent.click(screen.getByRole('button', {name: 'Add Variable'}));
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'foo');
		await appendToNewValue(screen.getByRole('group', {name: 'Value'}), 'abc');

		await expect.element(screen.getByRole('button', {name: 'Save'})).toBeDisabled();
	});

	it('should leave edit mode without saving when exiting', async ({worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}));
		const screen = await renderVariables();
		const row = variableRow(screen, 'amount');

		await userEvent.click(row.getByRole('button', {name: /^Edit variable /}));
		await appendToEditor(row.getByText('100', {exact: true}), '1', row);
		await userEvent.click(row.getByRole('button', {name: 'Exit'}));

		await expect.element(row.getByRole('button', {name: /^Copy amount: /})).toHaveTextContent('100');
		await expect.element(screen.getByRole('button', {name: 'Add Variable'})).toBeEnabled();
		await expectNoNotifications(screen);
	});

	it('should discard a pending new variable when the scope changes', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}),
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(
					createElementInstance({elementInstanceKey: ELEMENT_INSTANCE_KEY, elementId: 'task', state: 'ACTIVE'}),
				),
			}),
		);
		const screen = await renderVariables();

		await userEvent.click(screen.getByRole('button', {name: 'Add Variable'}));
		await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'foo');
		await userEvent.click(screen.getByRole('button', {name: 'Select element instance'}));

		await expect.element(screen.getByRole('button', {name: 'Add Variable'})).toBeEnabled();
		await expect.element(screen.getByRole('textbox', {name: 'Name'})).not.toBeInTheDocument();
	});

	it.for([
		{status: 500, title: 'Variable could not be saved'},
		{status: 403, title: "You don't have permission to perform this operation"},
	])(
		'should show an inline error until the value is edited when saving fails with $status',
		async ({status, title}, {worker}) => {
			worker.use(
				mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}),
				mockUpdateElementInstanceVariablesEndpoint({
					successResponse: HttpResponse.json(createProblemDetails({status}), {status}),
				}),
			);
			const screen = await renderVariables();

			await userEvent.click(screen.getByRole('button', {name: 'Add Variable'}));
			await userEvent.fill(screen.getByRole('textbox', {name: 'Name'}), 'foo');
			await appendToNewValue(screen.getByRole('group', {name: 'Value'}), '1');
			await userEvent.click(screen.getByRole('button', {name: 'Save'}));

			await expect.element(screen.getByRole('alert').filter({hasText: title})).toBeVisible();
			await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveValue('foo');
			await expectNoNotifications(screen);

			await appendToNewValue(screen.getByRole('group', {name: 'Value'}), '2');

			await expect.element(screen.getByText(title)).not.toBeInTheDocument();
		},
	);

	it('should edit and save a variable from its full value', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: variablesResponse([createVariable({name: 'amount', value: '100', variableKey: '1'})]),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createVariable({name: 'amount', value: '100', variableKey: '1'})),
			}),
			mockUpdateElementInstanceVariablesEndpoint({
				schema: z.strictObject({variables: z.strictObject({amount: z.literal(1005)}), local: z.literal(true)}),
				successResponse: savedResponse(),
				failureResponse: rejectedResponse(),
			}),
		);
		const screen = await renderVariables();

		await userEvent.click(screen.getByRole('button', {name: 'Open'}));
		const dialog = screen.getByRole('dialog', {name: 'Full value of amount'});
		await userEvent.click(dialog.getByRole('button', {name: 'Edit', exact: true}));
		await expect.element(screen.getByRole('dialog', {name: 'Edit Variable "amount"'})).toBeVisible();

		worker.use(
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createVariable({name: 'amount', value: '1005', variableKey: '1'})),
			}),
		);
		await appendToEditor(screen.getByRole('dialog').getByText('100', {exact: true}), '5', screen.getByRole('dialog'));
		await userEvent.click(screen.getByRole('button', {name: 'Apply'}));

		await expect.element(screen.getByText('Variable updated')).toBeVisible();
	});

	it.for([
		{scenario: 'a completed instance', processInstance: {state: 'COMPLETED'}, search: ''},
		{
			scenario: 'a user task on a suspended instance',
			processInstance: {state: 'SUSPENDED'},
			search: `?elementInstanceKey=${ELEMENT_INSTANCE_KEY}`,
		},
	] as const)('should not allow editing variables of $scenario', async ({processInstance, search}, {worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}),
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(
					createElementInstance({elementInstanceKey: ELEMENT_INSTANCE_KEY, type: 'USER_TASK', state: 'ACTIVE'}),
				),
			}),
		);
		const screen = await renderVariables({search, processInstance});
		const row = variableRow(screen, 'amount');

		await expect.element(row.getByRole('button', {name: 'Open'})).toBeVisible();
		await expect.element(row.getByRole('button', {name: /^Edit variable /})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Add Variable'})).toBeDisabled();
	});

	it('should allow editing variables of the root scope of a suspended instance', async ({worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}));
		const screen = await renderVariables({processInstance: {state: 'SUSPENDED'}});

		await expect.element(variableRow(screen, 'amount').getByRole('button', {name: /^Edit variable /})).toBeEnabled();
		await expect.element(screen.getByRole('button', {name: 'Add Variable'})).toBeEnabled();
	});

	it('should not allow editing variables of a finished element instance', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}),
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(
					createElementInstance({elementInstanceKey: ELEMENT_INSTANCE_KEY, state: 'COMPLETED'}),
				),
			}),
		);
		const screen = await renderVariables({search: `?elementInstanceKey=${ELEMENT_INSTANCE_KEY}`});
		const row = variableRow(screen, 'amount');

		await expect.element(row.getByRole('button', {name: 'Open'})).toBeVisible();
		await expect.element(row.getByRole('button', {name: /^Edit variable /})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Add Variable'})).toBeDisabled();
	});

	it('should keep a truncated variable read-only when its full value cannot be loaded', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: variablesResponse([
					createVariable({name: 'payload', value: '123', isTruncated: true, variableKey: '7'}),
				]),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderVariables();
		const row = variableRow(screen, 'payload');

		await userEvent.click(row.getByRole('button', {name: /^Edit variable /}));

		await expect.element(screen.getByText('Variable could not be fetched')).toBeVisible();
		await expect.element(row.getByRole('button', {name: /^Copy payload: /})).toBeVisible();
		await expect.element(row.getByRole('textbox', {name: 'payload'})).not.toBeInTheDocument();
		await expect.element(row.getByRole('button', {name: 'Save'})).toBeDisabled();
	});

	it('should load the full value of a truncated variable when retrying after a failure', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: variablesResponse([
					createVariable({name: 'payload', value: '123', isTruncated: true, variableKey: '7'}),
				]),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderVariables();
		const row = variableRow(screen, 'payload');

		await userEvent.click(row.getByRole('button', {name: /^Edit variable /}));
		await expect.element(row.getByRole('alert').filter({hasText: 'Variable could not be fetched'})).toBeVisible();
		await expectNoNotifications(screen);

		worker.use(
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createVariable({name: 'payload', value: '1234', variableKey: '7'})),
			}),
		);
		await userEvent.click(row.getByRole('button', {name: 'Try again'}));

		await expect.element(row.getByText('Variable could not be fetched')).not.toBeInTheDocument();
		await expect.element(row.getByRole('textbox', {name: 'payload'})).toBeInTheDocument();
		await expect.element(row.getByText('1234')).toBeVisible();
	});

	it.for([
		{scenario: 'no variables', items: [], title: 'Click to copy variables to clipboard'},
		{
			scenario: 'truncated values',
			items: [createVariable({isTruncated: true, value: '"abc'})],
			title: 'Copying is disabled for variable values larger than 8192 characters',
		},
	])('should disable copying variables for $scenario', async ({items, title}, {worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse(items)}));
		const screen = await renderVariables();

		await expect.element(screen.getByRole('button', {name: 'Copy variables'})).toBeDisabled();
		await expect.element(screen.getByRole('button', {name: 'Copy variables'})).toHaveAttribute('title', title);
	});

	it('should enable copying variables', async ({worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable()])}));
		const screen = await renderVariables();

		await expect.element(screen.getByRole('button', {name: 'Copy variables'})).toBeEnabled();
	});
});
