/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {userEvent, type Locator} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {useRouterState} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockGetProcessDefinitionXmlEndpoint, mockQueryVariablesEndpoint} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {ProcessInstanceVariables} from './ProcessInstanceVariables';
import {LastModification} from './LastModification';
import {processInstanceSearchSchema} from './processInstanceSearch';
import {useModificationController, useProcessInstanceModification} from './useProcessInstanceModification';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {addToken} from './modifications/mocks';

const PROCESS_INSTANCE_ID = '2251799813685280';
const NEW_SCOPE_KEY = 'new-scope';
const PLACEHOLDER_SEARCH = `?elementId=task&elementInstanceKey=${NEW_SCOPE_KEY}&isPlaceholder=true`;

afterEach(async () => {
	await cleanup();
});

async function appendToEditor(target: Locator, text: string) {
	await userEvent.click(target);
	await expect.poll(() => document.activeElement?.getAttribute('aria-roledescription')).toBe('editor');
	await userEvent.keyboard(`{End}${text}`);
}

async function appendToNewValue(group: Locator, text: string) {
	await expect.element(group.getByRole('textbox')).toBeInTheDocument();
	await appendToEditor(group, text);
}

function ModificationControls() {
	const {dispatch} = useProcessInstanceModification();
	const {clearSelection, selectPlaceholder} = useProcessInstanceElementSelection();

	return (
		<>
			<button type="button" onClick={() => dispatch({type: 'enableModificationMode'})}>
				Enter modification mode
			</button>
			<button
				type="button"
				onClick={() =>
					dispatch({type: 'addModification', modification: addToken({elementId: 'task', scopeId: NEW_SCOPE_KEY})})
				}
			>
				Plan token
			</button>
			<button type="button" onClick={clearSelection}>
				Select process instance
			</button>
			<button type="button" onClick={() => selectPlaceholder('task', NEW_SCOPE_KEY, false)}>
				Select planned instance
			</button>
			<LastModification />
		</>
	);
}

function renderVariables(search = '') {
	function Variables() {
		const routeSearch = useRouterState({select: (state) => state.location.search});
		const modification = useModificationController(PROCESS_INSTANCE_ID);

		return (
			<div style={{height: '400px', display: 'flex', flexDirection: 'column'}}>
				<ProcessInstancePageProvider
					processInstanceId={PROCESS_INSTANCE_ID}
					processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})}
					search={processInstanceSearchSchema.parse(routeSearch)}
					modification={modification}
				>
					<ModificationControls />
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

async function renderPlannedScope() {
	const screen = await renderVariables(PLACEHOLDER_SEARCH);
	await userEvent.click(screen.getByRole('button', {name: 'Enter modification mode'}));
	await userEvent.click(screen.getByRole('button', {name: 'Plan token'}));
	await expect.element(screen.getByText('The element has no variables')).toBeVisible();
	return {screen};
}

async function addNewVariable(screen: Awaited<ReturnType<typeof renderVariables>>, name: string, value: string) {
	await userEvent.click(screen.getByRole('button', {name: 'Add Variable'}));
	const nameInput = screen.getByTestId('new-variable-name').last();
	await expect.element(nameInput).toHaveFocus();
	await userEvent.keyboard(name);
	await appendToNewValue(screen.getByRole('group', {name: 'Value'}).last(), value);
	await userEvent.tab();
}

describe('<ProcessInstanceVariables /> modification mode', () => {
	it('should stage an edit of a root variable when a token is planned', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(
					createQueryVariablesByUserTaskResponse({items: [createVariable({name: 'amount', value: '100'})]}),
				),
			}),
		);
		const screen = await renderVariables();
		const row = screen.getByTestId('variable-amount');
		await userEvent.click(screen.getByRole('button', {name: 'Enter modification mode'}));
		await expect.element(row.getByRole('button', {name: 'Copy amount'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Add Variable'})).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Plan token'}));
		await appendToEditor(row.getByText('100', {exact: true}), '1');
		await userEvent.tab();

		await expect.element(screen.getByText('Edit variable "amount"')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Undo'}));

		await expect.element(screen.getByText('Edit variable "amount"')).not.toBeInTheDocument();
		await expect.element(row.getByText('100', {exact: true})).toBeVisible();
	});

	it('should stage a new variable in a planned scope', async ({worker}) => {
		worker.use(mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}));
		const {screen} = await renderPlannedScope();

		await addNewVariable(screen, 'foo', '1');

		await expect.element(screen.getByText('Add new variable "foo"')).toBeVisible();
		await expect.element(screen.getByText('The element has no variables')).not.toBeInTheDocument();
	});

	it('should validate new variables before staging them', async ({worker}) => {
		worker.use(mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}));
		const {screen} = await renderPlannedScope();

		await addNewVariable(screen, 'foo', '1');
		await addNewVariable(screen, 'foo', 'abc');

		await expect.element(screen.getByText('Name should be unique')).toBeVisible();
		await expect.element(screen.getByText('Value has to be JSON')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Add Variable'})).toBeDisabled();
		await expect.element(screen.getByText('Add new variable "foo"')).toBeVisible();
	});

	it('should remove a new variable together with its modification', async ({worker}) => {
		worker.use(mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}));
		const {screen} = await renderPlannedScope();
		await addNewVariable(screen, 'foo', '1');
		await expect.element(screen.getByText('Add new variable "foo"')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));

		await expect.element(screen.getByTestId('new-variable-name')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Add new variable "foo"')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Add "task"')).toBeVisible();
	});

	it('should remove a new variable when its modification is undone', async ({worker}) => {
		worker.use(mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}));
		const {screen} = await renderPlannedScope();
		await addNewVariable(screen, 'foo', '1');

		await userEvent.click(screen.getByRole('button', {name: 'Undo'}));

		await expect.element(screen.getByTestId('new-variable-name')).not.toBeInTheDocument();
		await expect.element(screen.getByText('The element has no variables')).toBeVisible();
	});

	it('should restore staged variables when returning to a scope', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse({items: [createVariable()]})),
			}),
		);
		const {screen} = await renderPlannedScope();
		await addNewVariable(screen, 'foo', '1');
		await expect.element(screen.getByText('Add new variable "foo"')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Select process instance'}));
		await expect.element(screen.getByTestId('new-variable-name')).not.toBeInTheDocument();
		await userEvent.click(screen.getByRole('button', {name: 'Select planned instance'}));

		await expect.element(screen.getByTestId('new-variable-name')).toHaveValue('foo');
		await expect.element(screen.getByRole('group', {name: 'Value'})).toHaveTextContent('1');
	});
});
