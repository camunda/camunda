/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useReducer} from 'react';
import {afterEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {useRouterState} from '@tanstack/react-router';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessInstanceSequenceFlowsEndpoint,
	mockGetProcessInstanceStatisticsEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockModifyProcessInstanceEndpoint,
	mockQueryAgentInstancesEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryElementInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createProcessDefinitionStatistic} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ProcessInstancePageProvider} from '../ProcessInstancePageProvider';
import {processInstanceSearchSchema} from '../processInstanceSearch';
import {modificationReducer, type Modification, type ModificationState} from '../modifications/modificationReducer';
import {isModificationModeEnabled} from '../modifications/modificationSelectors';
import {addToken, cancelToken, createModificationState, variable} from '../modifications/mocks';
import {InstanceDiagram} from './InstanceDiagram';
import {InstanceHistory} from './InstanceHistory';
import {ProcessInstanceModificationFooter} from './ProcessInstanceModificationFooter';

vi.mock('#/operate/shared/Editors/LazyMonacoEditor', () => import('#/shared-test-modules/lazy-monaco-editor'));

const PROCESS_INSTANCE_ID = '2251799813685280';
const PROCESS_XML = `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
<bpmn:process id="Process_1" isExecutable="true"><bpmn:startEvent id="start_1" name="Start" />
<bpmn:userTask id="task_1" name="Task A" /><bpmn:serviceTask id="task_2" name="Task B" /></bpmn:process>
<bpmndi:BPMNDiagram id="Diagram_1"><bpmndi:BPMNPlane id="Plane_1" bpmnElement="Process_1">
<bpmndi:BPMNShape id="start_di" bpmnElement="start_1"><dc:Bounds x="100" y="100" width="36" height="36" /></bpmndi:BPMNShape>
<bpmndi:BPMNShape id="task_1_di" bpmnElement="task_1"><dc:Bounds x="200" y="80" width="100" height="80" /></bpmndi:BPMNShape>
<bpmndi:BPMNShape id="task_2_di" bpmnElement="task_2"><dc:Bounds x="400" y="80" width="100" height="80" /></bpmndi:BPMNShape>
</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>`;
const TASK_INSTANCE = createElementInstance({
	elementId: 'task_1',
	elementName: 'Task A',
	elementInstanceKey: '2251799813685290',
	processInstanceKey: PROCESS_INSTANCE_ID,
});

function ModificationPage({
	initialState,
	processInstance,
}: {
	initialState: ModificationState;
	processInstance: ProcessInstance;
}) {
	const search = useRouterState({select: (state) => state.location.search});
	const [state, dispatch] = useReducer(modificationReducer, initialState);
	const isEnabled = isModificationModeEnabled(state);

	return (
		<ProcessInstancePageProvider
			processInstanceId={PROCESS_INSTANCE_ID}
			processInstance={processInstance}
			search={processInstanceSearchSchema.parse(search)}
			modification={{state, dispatch, isModificationModeEnabled: isEnabled}}
		>
			<div style={{height: '400px', width: '900px'}}>
				<InstanceDiagram />
			</div>
			<div style={{height: '300px', width: '900px'}}>
				<InstanceHistory />
			</div>
			{isEnabled ? (
				<ProcessInstanceModificationFooter />
			) : (
				<p>{`Modification status: ${state.status}, ${state.modifications.length} staged`}</p>
			)}
		</ProcessInstancePageProvider>
	);
}

function handlers({
	statistics = [createProcessDefinitionStatistic({elementId: 'task_1', active: 1})],
	elementInstances = [TASK_INSTANCE],
}: {
	statistics?: ReturnType<typeof createProcessDefinitionStatistic>[];
	elementInstances?: ReturnType<typeof createElementInstance>[];
} = {}) {
	return [
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(PROCESS_XML)}),
		mockGetProcessInstanceStatisticsEndpoint({successResponse: HttpResponse.json({items: statistics})}),
		mockGetProcessInstanceSequenceFlowsEndpoint({successResponse: HttpResponse.json({items: []})}),
		mockGetProcessInstanceWaitStateStatisticsEndpoint({successResponse: HttpResponse.json(createPaginatedResponse())}),
		mockQueryAgentInstancesEndpoint({successResponse: HttpResponse.json(createPaginatedResponse())}),
		mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createPaginatedResponse())}),
		mockQueryElementInstancesEndpoint({
			successResponse: HttpResponse.json(createQueryElementInstancesResponse(elementInstances)),
		}),
	] as const;
}

async function renderModificationPage({
	modifications = [],
	state,
	search = '',
}: {
	modifications?: Modification[];
	state?: Partial<ModificationState>;
	search?: string;
} = {}) {
	const screen = await renderWithRouter(
		() => (
			<ModificationPage
				initialState={createModificationState(modifications, state)}
				processInstance={createProcessInstance({
					processInstanceKey: PROCESS_INSTANCE_ID,
					processDefinitionId: 'Process_1',
				})}
			/>
		),
		{
			path: '/operate-preview/processes/$processInstanceId/details',
			initialEntry: `/operate-preview/processes/${PROCESS_INSTANCE_ID}/details${search}`,
		},
	);
	await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
	return screen;
}

function getDiagramElement(elementId: string) {
	return document.querySelector<SVGElement>(`[data-element-id="${elementId}"]`)!;
}

afterEach(async () => {
	await cleanup();
	notificationsStore.reset();
});

describe('DS process instance token modifications', () => {
	it('should stage a cancellation from the dropdown, badge it in the diagram and undo it', async ({worker}) => {
		worker.use(...handlers());
		const screen = await renderModificationPage({search: '?elementId=task_1'});

		await expect.element(screen.getByText('Element Modifications')).toBeVisible();
		await expect.element(screen.getByText('Selected running instances: 1')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Add single element instance'})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Cancel selected instance in this element'}));

		await expect.element(screen.getByText('Element Modifications')).not.toBeInTheDocument();
		await expect
			.element(screen.getByText(/^Last added modification:/))
			.toHaveTextContent('Last added modification: Cancel "Task A"');
		await expect.element(screen.getByRole('img', {name: '1 canceled'})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Undo'}));

		await expect.element(screen.getByRole('img', {name: '1 canceled'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Review Modifications'})).toBeDisabled();
	});

	it('should move all running instances to the target selected in the diagram', async ({worker}) => {
		worker.use(
			...handlers({
				statistics: [createProcessDefinitionStatistic({elementId: 'task_1', active: 2})],
				elementInstances: [TASK_INSTANCE, {...TASK_INSTANCE, elementInstanceKey: '2251799813685291'}],
			}),
		);
		const screen = await renderModificationPage({search: '?elementId=task_1'});

		await expect
			.element(screen.getByText('To modify a specific instance, select it in the Instance History below.'))
			.toBeVisible();
		await userEvent.click(
			screen.getByRole('button', {name: 'Move all running instances in this element to another target'}),
		);
		await expect.element(screen.getByText('Select the target element in the diagram')).toBeVisible();

		await userEvent.click(getDiagramElement('task_2'));

		await expect.element(screen.getByText('Select the target element in the diagram')).not.toBeInTheDocument();
		await expect
			.element(screen.getByText(/^Last added modification:/))
			.toHaveTextContent('Last added modification: Move "Task A" to "Task B"');
	});

	it('should discard a pending move from the banner', async ({worker}) => {
		worker.use(
			...handlers({
				statistics: [createProcessDefinitionStatistic({elementId: 'task_1', active: 2})],
				elementInstances: [TASK_INSTANCE, {...TASK_INSTANCE, elementInstanceKey: '2251799813685291'}],
			}),
		);
		const screen = await renderModificationPage({search: '?elementId=task_1'});

		await userEvent.click(
			screen.getByRole('button', {name: 'Move all running instances in this element to another target'}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Discard'}));

		await expect.element(screen.getByText('Select the target element in the diagram')).not.toBeInTheDocument();
		await expect.element(screen.getByText(/^Last added modification:/)).not.toBeInTheDocument();
	});

	it('should show staged tokens in the instance history', async ({worker}) => {
		worker.use(...handlers());
		const screen = await renderModificationPage({
			modifications: [
				addToken({elementId: 'task_2', scopeId: 'scope-1', element: {id: 'task_2', name: 'Task B'}}),
				cancelToken({elementId: 'task_1', elementInstanceKey: TASK_INSTANCE.elementInstanceKey}),
			],
		});
		const placeholder = screen.getByRole('treeitem', {name: /Task B, this element instance is planned to be added/});

		await expect.element(placeholder).toBeVisible();
		await expect
			.element(placeholder.getByRole('img', {name: 'This element instance is planned to be added'}))
			.toBeVisible();
		await expect
			.element(
				screen
					.getByRole('treeitem', {name: /Task A/})
					.getByRole('img', {name: 'This element instance is planned to be canceled'}),
			)
			.toBeVisible();

		await userEvent.click(placeholder);

		await expect
			.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search))
			.toMatchObject({elementId: 'task_2', elementInstanceKey: 'scope-1', isPlaceholder: true});
	});
});

describe('DS process instance modification footer', () => {
	it('should discard all modifications after confirming', async ({worker}) => {
		worker.use(...handlers());
		const screen = await renderModificationPage({modifications: [cancelToken({elementId: 'task_1'})]});

		await userEvent.click(screen.getByRole('button', {name: 'Discard All'}));
		await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));
		await expect.element(screen.getByRole('button', {name: 'Review Modifications'})).toBeEnabled();

		await userEvent.click(screen.getByRole('button', {name: 'Discard All'}));
		await expect.element(screen.getByRole('alertdialog', {name: 'Discard Modifications'})).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Discard', exact: true}));

		await expect.element(screen.getByText('Modification status: disabled, 0 staged')).toBeVisible();
	});

	it('should list staged modifications in the summary and remove them', async ({worker}) => {
		worker.use(...handlers());
		const screen = await renderModificationPage({
			modifications: [
				addToken({elementId: 'task_2', scopeId: 'scope-1', element: {id: 'task_2', name: 'Task B'}}),
				variable({
					operation: 'ADD_VARIABLE',
					scopeId: 'scope-1',
					name: 'foo',
					newValue: '"bar"',
					elementName: 'Task B',
				}),
			],
		});

		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
		const dialog = screen.getByRole('dialog', {name: 'Apply Modifications'});
		const rows = dialog.getByRole('row');

		await expect.element(rows.filter({hasText: 'Task B--1'})).toHaveTextContent('AddTask B--1Delete');
		await expect.element(rows.filter({hasText: 'foo: "bar"'})).toBeVisible();
		await expect.element(dialog.getByRole('textbox')).not.toBeInTheDocument();

		await userEvent.click(dialog.getByRole('button', {name: /View variable changes/}));
		await expect.element(dialog.getByRole('textbox')).toBeInTheDocument();

		await userEvent.click(dialog.getByRole('button', {name: 'Delete variable modification'}));
		await userEvent.click(dialog.getByRole('button', {name: 'Delete element modification'}));

		await expect.element(dialog.getByText('No planned element modifications')).toBeVisible();
		await expect.element(dialog.getByText('No planned variable modifications')).toBeVisible();
		await expect.element(dialog.getByRole('button', {name: 'Apply'})).toBeDisabled();
	});

	it('should apply the modifications and leave modification mode', async ({worker}) => {
		worker.use(
			...handlers(),
			mockModifyProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const screen = await renderModificationPage({modifications: [cancelToken({elementId: 'task_1'})]});

		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
		await userEvent.click(
			screen.getByRole('dialog', {name: 'Apply Modifications'}).getByRole('button', {name: 'Apply'}),
		);

		await expect.element(screen.getByText('Modification status: disabled, 0 staged')).toBeVisible();
	});
});
