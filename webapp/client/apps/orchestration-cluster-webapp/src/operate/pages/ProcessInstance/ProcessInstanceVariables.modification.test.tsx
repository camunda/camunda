/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeAll, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent, type Locator} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {useParams, useRouterState} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {preloadMonaco} from '#/vitest-modules/preload-monaco';
import {mockGetProcessDefinitionXmlEndpoint, mockQueryVariablesEndpoint} from '#/shared-test-modules/mock-handlers';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {ProcessInstance} from './ProcessInstance';
import {InstanceDiagram} from './InstanceDiagram';
import {InstanceHistory} from './InstanceHistory';
import {ProcessInstanceVariables} from './ProcessInstanceVariables';
import {ProcessInstanceOperations} from './ProcessInstanceOperations/ProcessInstanceOperations';
import {processInstanceSearchSchema} from './processInstanceSearch';

const PROCESS_INSTANCE_ID = '2251799813685280';
const PROCESS_XML = `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
<bpmn:process id="Process_1" isExecutable="true"><bpmn:userTask id="task_1" name="Task A" /></bpmn:process>
<bpmndi:BPMNDiagram id="Diagram_1"><bpmndi:BPMNPlane id="Plane_1" bpmnElement="Process_1">
<bpmndi:BPMNShape id="task_1_di" bpmnElement="task_1"><dc:Bounds x="200" y="80" width="100" height="80" /></bpmndi:BPMNShape>
</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>`;

const processInstance = createProcessInstance({
	processInstanceKey: PROCESS_INSTANCE_ID,
	processDefinitionId: 'Process_1',
});

function pageHandlers(variables: ReturnType<typeof createVariable>[] = []) {
	return [
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(PROCESS_XML)}),
		mockQueryVariablesEndpoint({
			successResponse: HttpResponse.json(createQueryVariablesByUserTaskResponse({items: variables})),
		}),
		...processInstanceHeaderHandlers(processInstance),
	] as const;
}

function Page() {
	const {processInstanceId = PROCESS_INSTANCE_ID} = useParams({strict: false});
	const search = useRouterState({select: (state) => state.location.search});

	return (
		<div style={{height: '900px', width: '1200px'}}>
			<ProcessInstance
				processInstanceId={processInstanceId}
				search={processInstanceSearchSchema.parse(search)}
				topPanel={<InstanceDiagram />}
				bottomPanel={
					<>
						<InstanceHistory />
						<ProcessInstanceVariables />
					</>
				}
				headerOperations={<ProcessInstanceOperations />}
			/>
		</div>
	);
}

async function renderPage(search = '') {
	localStorage.setItem('sharedState', JSON.stringify({hideModificationHelperModal: true}));
	const screen = await renderWithRouter(Page, {
		path: '/operate/processes/$processInstanceId/$',
		initialEntry: `/operate/processes/${PROCESS_INSTANCE_ID}/variables${search}`,
	});
	await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
	return screen;
}

async function enterModificationMode(screen: Awaited<ReturnType<typeof renderPage>>) {
	await userEvent.click(screen.getByRole('button', {name: `Modify Instance ${PROCESS_INSTANCE_ID}`}));
	await expect.element(screen.getByRole('button', {name: 'Review Modifications'})).toBeDisabled();
}

async function renderPlannedScope() {
	const screen = await renderPage();
	await enterModificationMode(screen);
	await userEvent.click(screen.getByText('Task A', {exact: true}).first());
	await userEvent.click(screen.getByRole('button', {name: 'Add single element instance'}));
	await userEvent.click(screen.getByRole('treeitem', {name: /planned to be added/}));
	await expect.element(screen.getByText('The element has no variables')).toBeVisible();
	return screen;
}

async function appendToEditor(target: Locator, text: string) {
	await userEvent.click(target);
	await expect.poll(() => document.activeElement?.getAttribute('aria-roledescription')).toBe('editor');
	await userEvent.keyboard(`{End}${text}`);
}

async function addNewVariable(screen: Awaited<ReturnType<typeof renderPage>>, name: string, value: string) {
	await userEvent.click(screen.getByRole('button', {name: 'Add Variable'}));
	const nameInput = screen.getByRole('textbox', {name: 'Name'}).last();
	await expect.element(nameInput).toHaveFocus();
	await userEvent.keyboard(name);
	const valueGroup = screen.getByRole('group', {name: 'Value'}).last();
	await expect.element(valueGroup.getByRole('textbox')).toBeInTheDocument();
	await appendToEditor(valueGroup, value);
	await userEvent.tab();
}

beforeEach(() => {
	vi.spyOn(window, 'matchMedia').mockImplementation((query) => ({
		matches: false,
		media: query,
		onchange: null,
		addEventListener: vi.fn(),
		removeEventListener: vi.fn(),
		addListener: vi.fn(),
		removeListener: vi.fn(),
		dispatchEvent: vi.fn(),
	}));
	sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	localStorage.setItem('operate.hideProcessInstanceHelperModal', 'true');
});

afterEach(async () => {
	await cleanup();
	vi.restoreAllMocks();
});

describe('<ProcessInstanceVariables /> modification mode', () => {
	beforeAll(preloadMonaco, 120_000);

	it('should stage an edit of a root variable and undo it', async ({worker}) => {
		worker.use(...pageHandlers([createVariable({name: 'amount', value: '100'})]));
		const screen = await renderPage();
		await enterModificationMode(screen);
		await userEvent.click(screen.getByText('Task A', {exact: true}).first());
		await userEvent.click(screen.getByRole('button', {name: 'Add single element instance'}));

		await appendToEditor(screen.getByText('100', {exact: true}), '1');
		await userEvent.tab();

		await expect.element(screen.getByText('Edit variable "amount"')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Undo'}));

		await expect.element(screen.getByText('Edit variable "amount"')).not.toBeInTheDocument();
		await expect.element(screen.getByText('100', {exact: true})).toBeVisible();
	});

	it('should stage a new variable in a planned scope', async ({worker}) => {
		worker.use(...pageHandlers());
		const screen = await renderPlannedScope();

		await addNewVariable(screen, 'foo', '1');

		await expect.element(screen.getByText('Add new variable "foo"')).toBeVisible();
		await expect.element(screen.getByText('The element has no variables')).not.toBeInTheDocument();
	});

	it('should validate new variables before staging them', async ({worker}) => {
		worker.use(...pageHandlers());
		const screen = await renderPlannedScope();

		await addNewVariable(screen, 'foo', '1');
		await addNewVariable(screen, 'foo', 'abc');

		await expect.element(screen.getByText('Name should be unique')).toBeVisible();
		await expect.element(screen.getByText('Value has to be JSON')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Add Variable'})).toBeDisabled();
		await expect.element(screen.getByText('Add new variable "foo"')).toBeVisible();
	});

	it('should remove a new variable together with its modification', async ({worker}) => {
		worker.use(...pageHandlers());
		const screen = await renderPlannedScope();
		await addNewVariable(screen, 'foo', '1');
		await expect.element(screen.getByText('Add new variable "foo"')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));

		await expect.element(screen.getByRole('textbox', {name: 'Name'})).not.toBeInTheDocument();
		await expect.element(screen.getByText('Add new variable "foo"')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Add "Task A"')).toBeVisible();
	});

	it('should remove a new variable when its modification is undone', async ({worker}) => {
		worker.use(...pageHandlers());
		const screen = await renderPlannedScope();
		await addNewVariable(screen, 'foo', '1');

		await userEvent.click(screen.getByRole('button', {name: 'Undo'}));

		await expect.element(screen.getByRole('textbox', {name: 'Name'})).not.toBeInTheDocument();
		await expect.element(screen.getByText('The element has no variables')).toBeVisible();
	});

	it('should restore staged variables when returning to a scope', async ({worker}) => {
		worker.use(...pageHandlers([createVariable()]));
		const screen = await renderPlannedScope();
		await addNewVariable(screen, 'foo', '1');
		await expect.element(screen.getByText('Add new variable "foo"')).toBeVisible();

		await userEvent.click(screen.getByRole('tree').getByText('My Process'));
		await expect.element(screen.getByRole('textbox', {name: 'Name'})).not.toBeInTheDocument();
		await userEvent.click(screen.getByRole('treeitem', {name: /planned to be added/}));

		await expect.element(screen.getByRole('textbox', {name: 'Name'})).toHaveValue('foo');
		await expect.element(screen.getByRole('group', {name: 'Value'})).toHaveTextContent('1');
	});
});
