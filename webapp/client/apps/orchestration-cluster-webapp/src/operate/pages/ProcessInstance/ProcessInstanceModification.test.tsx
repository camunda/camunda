/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useReducer} from 'react';
import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {useRouterState} from '@tanstack/react-router';
import isEqual from 'lodash/isEqual';
import {z} from 'zod';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetElementInstanceEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessInstanceSequenceFlowsEndpoint,
	mockGetProcessInstanceStatisticsEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockModifyProcessInstanceEndpoint,
	mockQueryAgentInstancesEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryVariablesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createProcessDefinitionStatistic} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {createVariable} from '#/shared-test-modules/api-mocks/variables';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {InstanceDiagram} from './InstanceDiagram';
import {InstanceHistory} from './InstanceHistory';
import {ProcessInstanceVariables} from './ProcessInstanceVariables';
import {ProcessInstanceModificationFooter} from './ProcessInstanceModificationFooter';
import {processInstanceSearchSchema} from './processInstanceSearch';
import {modificationReducer, type Modification, type ModificationState} from './modifications/modificationReducer';
import {isModificationModeEnabled} from './modifications/modificationSelectors';
import {addToken, cancelToken, createModificationState, moveToken, variable} from './modifications/mocks';

const PROCESS_INSTANCE_ID = '2251799813685280';
const PROCESS_XML = `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
<bpmn:process id="Process_1" isExecutable="true"><bpmn:startEvent id="start_1" name="Start" />
<bpmn:userTask id="task_1" name="Task A" /><bpmn:serviceTask id="task_2" name="Task B"><bpmn:multiInstanceLoopCharacteristics /></bpmn:serviceTask></bpmn:process>
<bpmndi:BPMNDiagram id="Diagram_1"><bpmndi:BPMNPlane id="Plane_1" bpmnElement="Process_1">
<bpmndi:BPMNShape id="start_di" bpmnElement="start_1"><dc:Bounds x="100" y="100" width="36" height="36" /></bpmndi:BPMNShape>
<bpmndi:BPMNShape id="task_1_di" bpmnElement="task_1"><dc:Bounds x="200" y="80" width="100" height="80" /></bpmndi:BPMNShape>
<bpmndi:BPMNShape id="task_2_di" bpmnElement="task_2"><dc:Bounds x="400" y="80" width="100" height="80" /></bpmndi:BPMNShape>
</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>`;
const SUB_PROCESS_XML = `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Definitions_2" targetNamespace="http://bpmn.io/schema/bpmn">
<bpmn:process id="Process_1" isExecutable="true"><bpmn:userTask id="task_1" name="Task A" />
<bpmn:subProcess id="sub_1" name="Sub Process"><bpmn:userTask id="inner_1" name="Inner Task" /></bpmn:subProcess></bpmn:process>
<bpmndi:BPMNDiagram id="Diagram_2"><bpmndi:BPMNPlane id="Plane_2" bpmnElement="Process_1">
<bpmndi:BPMNShape id="task_1_di" bpmnElement="task_1"><dc:Bounds x="100" y="100" width="100" height="80" /></bpmndi:BPMNShape>
<bpmndi:BPMNShape id="sub_1_di" bpmnElement="sub_1" isExpanded="true"><dc:Bounds x="250" y="60" width="300" height="180" /></bpmndi:BPMNShape>
<bpmndi:BPMNShape id="inner_1_di" bpmnElement="inner_1"><dc:Bounds x="350" y="100" width="100" height="80" /></bpmndi:BPMNShape>
</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>`;
const TASK_INSTANCE = createElementInstance({
	elementId: 'task_1',
	elementName: 'Task A',
	elementInstanceKey: '2251799813685290',
	processInstanceKey: PROCESS_INSTANCE_ID,
});

function createTaskInstances(count: number) {
	return Array.from({length: count}, (_, index) =>
		createElementInstance({...TASK_INSTANCE, elementInstanceKey: `22517998136853${10 + index}`}),
	);
}

function ModificationPage({
	initialState,
	processInstance,
	withVariables,
}: {
	initialState: ModificationState;
	processInstance: ProcessInstance;
	withVariables: boolean;
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
			{withVariables && <ProcessInstanceVariables />}
			{isEnabled ? (
				<ProcessInstanceModificationFooter />
			) : (
				<p>{`Modification status: ${state.status}, ${state.modifications.length} staged`}</p>
			)}
		</ProcessInstancePageProvider>
	);
}

function handlers({
	xml = PROCESS_XML,
	statistics = [createProcessDefinitionStatistic({elementId: 'task_1', active: 1})],
	elementInstances = [TASK_INSTANCE],
}: {
	xml?: string;
	statistics?: ReturnType<typeof createProcessDefinitionStatistic>[];
	elementInstances?: ReturnType<typeof createElementInstance>[];
} = {}) {
	return [
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(xml)}),
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
	processInstance = createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, processDefinitionId: 'Process_1'}),
	withVariables = false,
}: {
	modifications?: Modification[];
	state?: Partial<ModificationState>;
	search?: string;
	processInstance?: ProcessInstance;
	withVariables?: boolean;
} = {}) {
	const screen = await renderWithRouter(
		() => (
			<ModificationPage
				initialState={createModificationState(modifications, state)}
				processInstance={processInstance}
				withVariables={withVariables}
			/>
		),
		{
			path: '/operate/processes/$processInstanceId/details',
			initialEntry: `/operate/processes/${PROCESS_INSTANCE_ID}/details${search}`,
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

describe('Process instance token modifications', () => {
	it('should stage a single-instance cancellation, clear the selection and undo it', async ({worker}) => {
		worker.use(...handlers());
		const screen = await renderModificationPage({search: '?elementId=task_1'});
		const dropdown = screen.getByTestId('popover');

		await expect.element(dropdown.getByText('Element Modifications')).toBeVisible();
		await expect.element(dropdown.getByText('Selected running instances: 1')).toBeVisible();
		await expect.element(dropdown.getByRole('button', {name: 'Add single element instance'})).toBeVisible();
		await expect
			.element(dropdown.getByRole('button', {name: 'Move selected instance in this element to another target'}))
			.toBeVisible();
		await expect
			.element(dropdown.getByRole('button', {name: 'Cancel all running element instances in this element'}))
			.not.toBeInTheDocument();

		await userEvent.click(dropdown.getByRole('button', {name: 'Cancel selected instance in this element'}));

		await expect
			.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search).elementId)
			.toBeUndefined();
		await expect.element(dropdown).not.toBeInTheDocument();
		await expect
			.element(screen.getByText(/^Last added modification:/))
			.toHaveTextContent('Last added modification: Cancel "Task A"');
		await expect.element(screen.getByTestId('modifications-overlay')).toHaveTextContent('1');

		await screen.router.navigate({
			to: '/operate/processes/$processInstanceId/details',
			params: {processInstanceId: PROCESS_INSTANCE_ID},
			search: {elementId: 'task_1'},
		});
		await expect.element(screen.getByTestId('popover').getByText('Selected running instances: 1')).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Undo'}));

		await expect.element(screen.getByText(/^Last added modification:/)).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('modifications-overlay')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Review Modifications'})).toBeDisabled();
		await expect
			.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search).elementId)
			.toBeUndefined();
	});

	it('should move all running instances to the target selected in the diagram', async ({worker}) => {
		worker.use(
			...handlers({
				statistics: [createProcessDefinitionStatistic({elementId: 'task_1', active: 2, incidents: 1})],
				elementInstances: createTaskInstances(3),
			}),
		);
		const screen = await renderModificationPage({search: '?elementId=task_1'});
		const dropdown = screen.getByTestId('popover');

		await expect.element(dropdown.getByText('Selected running instances: 3')).toBeVisible();
		await expect
			.element(dropdown.getByText('To modify a specific instance, select it in the Instance History below.'))
			.toBeVisible();
		await expect
			.element(dropdown.getByRole('button', {name: 'Cancel all running element instances in this element'}))
			.toBeVisible();

		await userEvent.click(dropdown.getByRole('button', {name: 'Move all'}));
		await expect.element(screen.getByText('Select the target element in the diagram')).toBeVisible();
		await expect.element(screen.getByTestId('popover')).not.toBeInTheDocument();

		await userEvent.click(getDiagramElement('task_2'));

		await expect.element(screen.getByText('Select the target element in the diagram')).not.toBeInTheDocument();
		await expect
			.element(screen.getByText(/^Last added modification:/))
			.toHaveTextContent('Last added modification: Move "Task A" to "Task B"');

		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
		const row = screen.getByRole('dialog', {name: 'Apply Modifications'}).getByRole('row').nth(1);
		await expect.element(row).toHaveTextContent('MoveTask A → Task B--3Delete');
	});

	it('should discard a pending move from the banner', async ({worker}) => {
		worker.use(
			...handlers({
				statistics: [createProcessDefinitionStatistic({elementId: 'task_1', active: 2})],
				elementInstances: createTaskInstances(2),
			}),
		);
		const screen = await renderModificationPage({search: '?elementId=task_1'});

		await userEvent.click(screen.getByTestId('popover').getByRole('button', {name: 'Move all'}));
		await userEvent.click(screen.getByRole('button', {name: 'Discard'}));

		await expect.element(screen.getByText('Select the target element in the diagram')).not.toBeInTheDocument();
		await expect.element(screen.getByText(/^Last added modification:/)).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Review Modifications'})).toBeDisabled();
	});

	it.for([
		{search: '?elementId=start_1', message: 'Unsupported element type'},
		{search: '?elementId=task_1&elementInstanceKey=2251799813685291', message: 'No modifications available'},
	])('should show "$message" when the selection cannot be modified', async ({search, message}, {worker}) => {
		worker.use(
			...handlers(),
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(
					createElementInstance({...TASK_INSTANCE, elementInstanceKey: '2251799813685291', state: 'COMPLETED'}),
				),
			}),
		);
		const screen = await renderModificationPage({search});

		await expect.element(screen.getByTestId('popover').getByText(message)).toBeVisible();
		await expect.element(screen.getByTestId('popover').getByRole('button')).not.toBeInTheDocument();
	});

	it.for([
		{search: '?elementId=task_2', label: 'an inner instance', isAddAvailable: false},
		{search: '?elementId=task_2&isMultiInstanceBody=true', label: 'the body', isAddAvailable: true},
	])(
		'should offer adding to a multi-instance element only through $label',
		async ({search, isAddAvailable}, {worker}) => {
			worker.use(
				...handlers({
					statistics: [createProcessDefinitionStatistic({elementId: 'task_2', active: 2})],
					elementInstances: createTaskInstances(2),
				}),
			);
			const screen = await renderModificationPage({search});
			const dropdown = screen.getByTestId('popover');
			const addButton = dropdown.getByRole('button', {name: 'Add single element instance'});

			await expect
				.element(dropdown.getByRole('button', {name: 'Cancel all running element instances in this element'}))
				.toBeVisible();
			if (isAddAvailable) {
				await expect.element(addButton).toBeVisible();
			} else {
				await expect.element(addButton).not.toBeInTheDocument();
			}
		},
	);

	it('should add a token in the parent scope selected in the instance history', async ({worker}) => {
		worker.use(
			...handlers({
				xml: SUB_PROCESS_XML,
				statistics: [createProcessDefinitionStatistic({elementId: 'sub_1', active: 2})],
				elementInstances: [
					createElementInstance({
						...TASK_INSTANCE,
						elementId: 'sub_1',
						elementName: 'Sub Process',
						type: 'SUB_PROCESS',
						elementInstanceKey: '2251799813685301',
					}),
					createElementInstance({
						...TASK_INSTANCE,
						elementId: 'sub_1',
						elementName: 'Sub Process',
						type: 'SUB_PROCESS',
						elementInstanceKey: '2251799813685302',
					}),
				],
			}),
		);
		const screen = await renderModificationPage({search: '?elementId=inner_1'});

		await userEvent.click(screen.getByTestId('popover').getByRole('button', {name: 'Add single element instance'}));
		await expect
			.element(
				screen.getByText('Element has multiple parent scopes. Please select parent node from Instance History to Add.'),
			)
			.toBeVisible();

		await userEvent.click(screen.getByRole('treeitem', {name: 'Sub Process'}).first());

		await expect
			.element(screen.getByText(/^Last added modification:/))
			.toHaveTextContent('Last added modification: Add "Inner Task"');
		await expect
			.element(screen.getByText('Element has multiple parent scopes.', {exact: false}))
			.not.toBeInTheDocument();
	});

	it('should only accept running instances as the parent scope for an added token', async ({worker}) => {
		worker.use(
			...handlers({
				xml: SUB_PROCESS_XML,
				statistics: [createProcessDefinitionStatistic({elementId: 'sub_1', active: 2})],
				elementInstances: [
					createElementInstance({
						...TASK_INSTANCE,
						elementId: 'sub_1',
						elementName: 'Sub Process',
						type: 'SUB_PROCESS',
						state: 'COMPLETED',
						elementInstanceKey: '2251799813685303',
					}),
					createElementInstance({
						...TASK_INSTANCE,
						elementId: 'sub_1',
						elementName: 'Sub Process',
						type: 'SUB_PROCESS',
						elementInstanceKey: '2251799813685304',
					}),
				],
			}),
		);
		const screen = await renderModificationPage({
			modifications: [
				addToken({elementId: 'inner_1', scopeId: 'planned-task', parentScopeIds: {sub_1: 'planned-sub'}}),
			],
			state: {status: 'adding-token', sourceElementIdForAddOperation: 'inner_1'},
		});
		const banner = screen.getByText(
			'Element has multiple parent scopes. Please select parent node from Instance History to Add.',
		);
		const subProcessInstances = screen.getByRole('treeitem', {name: 'Sub Process', exact: true});

		await userEvent.click(
			screen.getByRole('treeitem', {name: 'Sub Process, this element instance is planned to be added'}),
		);
		await userEvent.click(subProcessInstances.nth(0));
		await expect.element(banner).toBeVisible();

		await userEvent.click(subProcessInstances.nth(1));
		await expect.element(banner).not.toBeInTheDocument();
		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
		const rows = screen.getByRole('dialog', {name: 'Apply Modifications'}).getByRole('row');
		await expect.element(rows.nth(2)).toHaveTextContent('AddInner Task--1Delete');
		await expect.element(rows.nth(3)).not.toBeInTheDocument();
	});

	it('should cancel every running sub process instance when cancelling all from a selected instance', async ({
		worker,
	}) => {
		const subProcessInstance = createElementInstance({
			...TASK_INSTANCE,
			elementId: 'sub_1',
			elementName: 'Sub Process',
			type: 'SUB_PROCESS',
			elementInstanceKey: '2251799813685305',
		});
		worker.use(
			...handlers({
				xml: SUB_PROCESS_XML,
				statistics: [createProcessDefinitionStatistic({elementId: 'sub_1', active: 2})],
				elementInstances: [subProcessInstance],
			}),
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(subProcessInstance)}),
		);
		const screen = await renderModificationPage({
			search: `?elementId=sub_1&elementInstanceKey=${subProcessInstance.elementInstanceKey}`,
		});
		const dropdown = screen.getByTestId('popover');

		await expect.element(dropdown.getByText('Selected running instances: 1')).toBeVisible();
		await userEvent.click(dropdown.getByRole('button', {name: 'Cancel all running element instances in this element'}));
		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));

		await expect
			.element(
				screen
					.getByRole('dialog', {name: 'Apply Modifications'})
					.getByText('Applying these modifications will cancel the entire process instance.', {exact: false}),
			)
			.toBeVisible();
	});

	it('should keep a shared new parent scope when the first token inside it is deleted', async ({worker}) => {
		worker.use(...handlers({xml: SUB_PROCESS_XML, statistics: [], elementInstances: []}));
		const screen = await renderModificationPage({search: '?elementId=inner_1'});
		const addButton = screen.getByTestId('popover').getByRole('button', {name: 'Add single element instance'});
		const plannedSubProcess = screen.getByRole('treeitem', {
			name: 'Sub Process, this element instance is planned to be added',
		});

		await userEvent.click(addButton);
		await screen.router.navigate({
			to: '/operate/processes/$processInstanceId/details',
			params: {processInstanceId: PROCESS_INSTANCE_ID},
			search: {elementId: 'inner_1'},
		});
		await userEvent.click(addButton);
		await expect.element(plannedSubProcess).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
		const dialog = screen.getByRole('dialog', {name: 'Apply Modifications'});
		await userEvent.click(dialog.getByRole('button', {name: 'Delete element modification'}).first());
		await userEvent.click(dialog.getByRole('button', {name: 'Cancel'}));

		await expect.element(plannedSubProcess).toBeVisible();
		await userEvent.click(plannedSubProcess);
		await expect
			.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search))
			.toMatchObject({elementId: 'sub_1', isPlaceholder: true});
	});

	it('should add a token inside the single running sub process instance without planning a new one', async ({
		worker,
	}) => {
		worker.use(
			...handlers({
				xml: SUB_PROCESS_XML,
				statistics: [createProcessDefinitionStatistic({elementId: 'sub_1', active: 1})],
				elementInstances: [
					createElementInstance({
						...TASK_INSTANCE,
						elementId: 'sub_1',
						elementName: 'Sub Process',
						type: 'SUB_PROCESS',
						elementInstanceKey: '2251799813685306',
					}),
				],
			}),
		);
		const screen = await renderModificationPage({search: '?elementId=inner_1'});

		await userEvent.click(screen.getByTestId('popover').getByRole('button', {name: 'Add single element instance'}));

		await expect
			.element(screen.getByText(/^Last added modification:/))
			.toHaveTextContent('Last added modification: Add "Inner Task"');
		await expect.element(screen.getByRole('treeitem', {name: 'Sub Process', exact: true})).toBeVisible();
		await expect
			.element(screen.getByRole('treeitem', {name: 'Sub Process, this element instance is planned to be added'}))
			.not.toBeInTheDocument();
	});

	it('should not accept a parent scope that is planned to be cancelled', async ({worker}) => {
		const subProcessInstance = createElementInstance({
			...TASK_INSTANCE,
			elementId: 'sub_1',
			elementName: 'Sub Process',
			type: 'SUB_PROCESS',
			elementInstanceKey: '2251799813685307',
		});
		worker.use(
			...handlers({
				xml: SUB_PROCESS_XML,
				statistics: [createProcessDefinitionStatistic({elementId: 'sub_1', active: 2})],
				elementInstances: [subProcessInstance],
			}),
		);
		const screen = await renderModificationPage({
			modifications: [cancelToken({elementId: 'sub_1', affectedTokenCount: 2, visibleAffectedTokenCount: 2})],
			state: {status: 'adding-token', sourceElementIdForAddOperation: 'inner_1'},
		});
		const subProcess = screen.getByRole('treeitem', {name: 'Sub Process', exact: true});

		await expect.element(subProcess.getByTestId('cancel-icon')).toBeInTheDocument();
		await userEvent.click(subProcess);

		await expect.element(screen.getByText('Element has multiple parent scopes.', {exact: false})).toBeVisible();
		await expect
			.element(screen.getByText(/^Last added modification:/))
			.toHaveTextContent('Last added modification: Cancel "sub_1"');
	});

	it('should show a token moved into a multi-scope parent under each running parent instance', async ({worker}) => {
		const subProcessInstances = ['2251799813685308', '2251799813685309'].map((elementInstanceKey) =>
			createElementInstance({
				...TASK_INSTANCE,
				elementId: 'sub_1',
				elementName: 'Sub Process',
				type: 'SUB_PROCESS',
				elementInstanceKey,
			}),
		);
		worker.use(
			mockQueryElementInstancesEndpoint({
				schema: z.object({filter: z.object({elementInstanceScopeKey: z.literal(PROCESS_INSTANCE_ID)})}),
				successResponse: HttpResponse.json(createQueryElementInstancesResponse(subProcessInstances)),
				failureResponse: HttpResponse.json(createQueryElementInstancesResponse([])),
			}),
			...handlers({
				xml: SUB_PROCESS_XML,
				statistics: [createProcessDefinitionStatistic({elementId: 'sub_1', active: 2})],
				elementInstances: subProcessInstances,
			}),
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(subProcessInstances[0])}),
		);
		const screen = await renderModificationPage({
			modifications: [
				moveToken({elementId: 'task_1', targetElementId: 'inner_1', scopeIds: ['moved'], parentScopeIds: {}}),
			],
		});
		const subProcesses = screen.getByRole('treeitem', {name: 'Sub Process', exact: true});

		await expect.element(subProcesses.nth(1)).toBeVisible();
		for (const subProcess of subProcesses.elements()) {
			await userEvent.click(
				subProcess.querySelector<HTMLElement>(':scope > .cds--tree-node__label > .cds--tree-parent-node__toggle')!,
			);
		}

		const placeholders = screen.getByRole('treeitem', {
			name: 'Inner Task, this element instance is planned to be added',
		});
		await expect.element(placeholders.nth(0)).toBeVisible();
		await expect.element(placeholders.nth(1)).toBeVisible();
	});

	it('should place a token added in a multi-instance sub process under its inner instance only', async ({worker}) => {
		const [body, inner] = [
			{type: 'MULTI_INSTANCE_BODY' as const, elementInstanceKey: '2251799813685311'},
			{type: 'SUB_PROCESS' as const, elementInstanceKey: '2251799813685312'},
		].map((instance) =>
			createElementInstance({...TASK_INSTANCE, elementId: 'sub_1', elementName: 'Sub Process', ...instance}),
		);
		worker.use(
			mockQueryElementInstancesEndpoint({
				schema: z.object({filter: z.object({elementInstanceScopeKey: z.literal(PROCESS_INSTANCE_ID)})}),
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([body!, inner!])),
				failureResponse: HttpResponse.json(createQueryElementInstancesResponse([])),
			}),
			...handlers({
				xml: SUB_PROCESS_XML.replace(
					'<bpmn:subProcess id="sub_1" name="Sub Process">',
					'<bpmn:subProcess id="sub_1" name="Sub Process"><bpmn:multiInstanceLoopCharacteristics />',
				),
				statistics: [createProcessDefinitionStatistic({elementId: 'sub_1', active: 1})],
			}),
		);
		const screen = await renderModificationPage({
			modifications: [addToken({elementId: 'inner_1', scopeId: 'added', element: {id: 'inner_1', name: 'Inner Task'}})],
		});
		const subProcesses = screen.getByRole('treeitem', {name: /^Sub Process/});

		await expect.element(subProcesses.nth(1)).toBeVisible();
		for (const subProcess of subProcesses.elements()) {
			await userEvent.click(
				subProcess.querySelector<HTMLElement>(':scope > .cds--tree-node__label > .cds--tree-parent-node__toggle')!,
			);
		}

		const placeholder = screen.getByRole('treeitem', {
			name: 'Inner Task, this element instance is planned to be added',
		});
		await expect.element(placeholder).toBeVisible();
		await expect
			.element(screen.getByRole('treeitem', {name: 'Sub Process', exact: true}).getByRole('treeitem'))
			.toBeVisible();
	});

	it('should show staged tokens in the instance history until they are undone', async ({worker}) => {
		worker.use(...handlers());
		const screen = await renderModificationPage({
			modifications: [
				addToken({elementId: 'task_2', scopeId: 'scope-1', element: {id: 'task_2', name: 'Task B'}}),
				cancelToken({elementId: 'task_1', elementInstanceKey: TASK_INSTANCE.elementInstanceKey}),
			],
		});
		const placeholder = screen.getByRole('treeitem', {name: 'Task B, this element instance is planned to be added'});

		await expect.element(placeholder).toBeVisible();
		await expect.element(placeholder.getByTestId('add-icon')).toBeInTheDocument();
		await expect.element(placeholder.getByTestId('warning-icon')).toBeInTheDocument();
		await expect.element(screen.getByRole('treeitem', {name: 'Task A'}).getByTestId('cancel-icon')).toBeInTheDocument();

		await userEvent.click(placeholder);
		await expect
			.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search))
			.toMatchObject({elementId: 'task_2', elementInstanceKey: 'scope-1', isPlaceholder: true});

		await userEvent.click(screen.getByRole('button', {name: 'Undo'}));
		await expect.element(screen.getByTestId('cancel-icon')).not.toBeInTheDocument();
		await expect
			.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search).elementId)
			.toBe('task_2');

		await userEvent.click(screen.getByRole('button', {name: 'Undo'}));
		await expect.element(placeholder).not.toBeInTheDocument();
	});

	it('should frame the selection when the element has several running instances including incidents', async ({
		worker,
	}) => {
		worker.use(
			...handlers({
				statistics: [createProcessDefinitionStatistic({elementId: 'task_1', active: 1, incidents: 1})],
				elementInstances: createTaskInstances(2),
			}),
		);
		await renderModificationPage({search: '?elementId=task_1'});

		await expect.poll(() => getDiagramElement('task_1').classList.contains('op-selected-frame')).toBe(true);
	});
});

describe('Process instance modification summary', () => {
	it('should list staged modifications in order and remove them', async ({worker}) => {
		worker.use(...handlers({statistics: [createProcessDefinitionStatistic({elementId: 'task_1', active: 3})]}));
		const screen = await renderModificationPage({
			modifications: [
				addToken({elementId: 'task_2', scopeId: 'scope-1', element: {id: 'task_2', name: 'Task B'}}),
				cancelToken({elementId: 'task_1', element: {id: 'task_1', name: 'Task A'}, elementInstanceKey: 'instance-1'}),
				moveToken({
					elementId: 'task_1',
					targetElementId: 'task_2',
					element: {id: 'task_1', name: 'Task A'},
					targetElement: {id: 'task_2', name: 'Task B'},
					affectedTokenCount: 2,
				}),
				variable({
					operation: 'ADD_VARIABLE',
					scopeId: 'scope-1',
					name: 'foo',
					newValue: '"bar"',
					elementName: 'Task B',
				}),
				variable({
					operation: 'EDIT_VARIABLE',
					scopeId: PROCESS_INSTANCE_ID,
					name: 'count',
					oldValue: '1',
					newValue: '2',
					elementName: 'My Process',
				}),
			],
		});

		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
		const dialog = screen.getByRole('dialog', {name: 'Apply Modifications'});
		const rows = dialog.getByRole('row');

		await expect
			.element(
				dialog.getByText(`Planned modifications for Process Instance "My Process - ${PROCESS_INSTANCE_ID}".`, {
					exact: false,
				}),
			)
			.toBeVisible();
		await expect.element(rows.nth(1)).toHaveTextContent('AddTask B--1Delete');
		await expect.element(rows.nth(2)).toHaveTextContent('CancelTask Ainstance-11Delete');
		await expect.element(rows.nth(3)).toHaveTextContent('MoveTask A → Task B--2Delete');
		await expect.element(rows.filter({hasText: 'foo: "bar"'})).toMatchTextContent('AddTask Bfoo: "bar"');
		await expect.element(rows.filter({hasText: 'count: 2'})).toMatchTextContent('EditMy Processcount: 2');
		await expect.element(dialog.getByRole('button', {name: 'Apply'})).toBeEnabled();

		const deleteElementButton = dialog.getByRole('button', {name: 'Delete element modification'}).first();
		const deleteVariableButton = dialog.getByRole('button', {name: 'Delete variable modification'}).first();
		await userEvent.click(deleteElementButton);
		await expect.element(rows.nth(1)).toHaveTextContent('CancelTask Ainstance-11Delete');
		await userEvent.click(deleteElementButton);
		await userEvent.click(deleteElementButton);
		await userEvent.click(deleteVariableButton);
		await expect.element(dialog.getByText('foo: "bar"')).not.toBeInTheDocument();
		await userEvent.click(deleteVariableButton);

		await expect.element(dialog.getByText('No planned element modifications')).toBeVisible();
		await expect.element(dialog.getByText('No planned variable modifications')).toBeVisible();
		await expect.element(dialog.getByRole('button', {name: 'Apply'})).toBeDisabled();
	});

	it('should load a variable editor only when its row is expanded', async ({worker}) => {
		worker.use(...handlers());
		const screen = await renderModificationPage({
			modifications: [
				addToken({elementId: 'task_2', scopeId: 'scope-1'}),
				variable({operation: 'ADD_VARIABLE', scopeId: 'scope-1', name: 'foo', newValue: '"bar"'}),
			],
		});

		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
		const dialog = screen.getByRole('dialog', {name: 'Apply Modifications'});
		const variableRow = dialog.getByRole('row').filter({hasText: 'foo: "bar"'});
		await expect.element(variableRow).toBeVisible();
		await expect.element(dialog.getByRole('textbox')).not.toBeInTheDocument();

		await userEvent.click(variableRow.getByRole('button', {name: 'Expand current row'}));
		await expect.element(dialog.getByRole('textbox')).toBeInTheDocument();
	});

	it('should not apply moving all tokens out of a sub process that is planned to be cancelled', async ({worker}) => {
		worker.use(
			...handlers({
				xml: SUB_PROCESS_XML,
				statistics: [
					createProcessDefinitionStatistic({elementId: 'sub_1', active: 1}),
					createProcessDefinitionStatistic({elementId: 'inner_1', active: 1}),
				],
			}),
		);
		const screen = await renderModificationPage({
			modifications: [
				moveToken({elementId: 'inner_1', targetElementId: 'task_1', scopeIds: ['moved']}),
				cancelToken({elementId: 'sub_1', affectedTokenCount: 1}),
			],
		});

		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
		const dialog = screen.getByRole('dialog', {name: 'Apply Modifications'});

		await expect
			.element(dialog.getByText('Some planned moves take all tokens of an element inside a scope', {exact: false}))
			.toBeVisible();
		await expect
			.element(
				dialog.getByText('Applying these modifications will cancel the entire process instance.', {exact: false}),
			)
			.toBeVisible();
		await expect.element(dialog.getByRole('button', {name: 'Apply'})).toBeDisabled();
	});

	it('should reload the instance history after modifications are applied', async ({worker}) => {
		const appliedInstance = createElementInstance({...TASK_INSTANCE, elementId: 'task_2', elementName: 'Task B'});
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([TASK_INSTANCE])),
				once: true,
			}),
			...handlers({elementInstances: [appliedInstance]}),
			mockModifyProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const screen = await renderModificationPage({
			modifications: [cancelToken({elementId: 'task_1'})],
			processInstance: createProcessInstance({
				processInstanceKey: PROCESS_INSTANCE_ID,
				processDefinitionId: 'Process_1',
				state: 'COMPLETED',
			}),
		});
		await expect.element(screen.getByRole('treeitem', {name: 'Task A'})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
		await userEvent.click(
			screen.getByRole('dialog', {name: 'Apply Modifications'}).getByRole('button', {name: 'Apply'}),
		);

		await expect.element(screen.getByRole('treeitem', {name: 'Task B'})).toBeVisible();
	});

	it('should warn about cancelling the whole instance when more tokens run after cancelling all of them', async ({
		worker,
	}) => {
		worker.use(
			mockGetProcessInstanceStatisticsEndpoint({
				successResponse: HttpResponse.json({
					items: [createProcessDefinitionStatistic({elementId: 'task_1', active: 2})],
				}),
				once: true,
			}),
			...handlers({statistics: [createProcessDefinitionStatistic({elementId: 'task_1', active: 3})]}),
		);
		const screen = await renderModificationPage({
			modifications: [cancelToken({elementId: 'task_1', affectedTokenCount: 2, visibleAffectedTokenCount: 2})],
		});

		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));

		await expect
			.element(
				screen
					.getByRole('dialog', {name: 'Apply Modifications'})
					.getByText('Applying these modifications will cancel the entire process instance.', {exact: false}),
			)
			.toBeVisible();
	});

	it('should reload the instance variables after modifications are applied', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({items: [createVariable({name: 'count', value: '1', variableKey: '1'})]}),
				),
				once: true,
			}),
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({items: [createVariable({name: 'count', value: '2', variableKey: '1'})]}),
				),
			}),
			...handlers(),
			mockModifyProcessInstanceEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const screen = await renderModificationPage({
			modifications: [cancelToken({elementId: 'task_1', elementInstanceKey: TASK_INSTANCE.elementInstanceKey})],
			withVariables: true,
		});
		await expect.element(screen.getByRole('button', {name: 'Copy count'})).toHaveTextContent('1');

		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
		await userEvent.click(
			screen.getByRole('dialog', {name: 'Apply Modifications'}).getByRole('button', {name: 'Apply'}),
		);

		await expect.element(screen.getByRole('button', {name: 'Copy count'})).toHaveTextContent('2');
	});

	it('should not apply tokens added inside a sub process that is planned to be cancelled', async ({worker}) => {
		worker.use(
			...handlers({
				xml: SUB_PROCESS_XML,
				statistics: [createProcessDefinitionStatistic({elementId: 'sub_1', active: 1})],
			}),
		);
		const screen = await renderModificationPage({
			modifications: [
				cancelToken({elementId: 'sub_1', affectedTokenCount: 1}),
				addToken({elementId: 'inner_1', scopeId: 'inner-scope'}),
			],
		});

		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
		const dialog = screen.getByRole('dialog', {name: 'Apply Modifications'});

		await expect
			.element(dialog.getByText('target elements inside a scope that is planned to be canceled', {exact: false}))
			.toBeVisible();
		await expect.element(dialog.getByRole('button', {name: 'Apply'})).toBeDisabled();
	});

	it.for([
		{
			name: 'orphaned variable modifications',
			modifications: [variable({operation: 'ADD_VARIABLE', scopeId: 'unknown-scope', name: 'foo', newValue: '1'})],
			hasParent: false,
			message: 'Some planned variable modifications cannot be applied.',
			isApplyDisabled: true,
		},
		{
			name: 'cancelling a child instance entirely',
			modifications: [cancelToken({elementId: 'task_1', affectedTokenCount: 1})],
			hasParent: true,
			message: 'This instance is a child instance of Parent Process - 2251799813685270',
			isApplyDisabled: true,
		},
		{
			name: 'cancelling a root instance entirely',
			modifications: [cancelToken({elementId: 'task_1', affectedTokenCount: 1})],
			hasParent: false,
			message: 'Applying these modifications will cancel the entire process instance.',
			isApplyDisabled: false,
		},
	])('should validate $name', async ({modifications, hasParent, message, isApplyDisabled}, {worker}) => {
		worker.use(
			...handlers(),
			mockGetProcessInstanceCallHierarchyEndpoint({
				successResponse: HttpResponse.json([
					createCallHierarchy({processInstanceKey: '2251799813685260', processDefinitionName: 'Root Process'}),
					createCallHierarchy({processInstanceKey: '2251799813685270', processDefinitionName: 'Parent Process'}),
					createCallHierarchy({processInstanceKey: PROCESS_INSTANCE_ID, processDefinitionName: 'My Process'}),
				]),
			}),
		);
		const screen = await renderModificationPage({
			modifications,
			processInstance: createProcessInstance({
				processInstanceKey: PROCESS_INSTANCE_ID,
				processDefinitionId: 'Process_1',
				parentProcessInstanceKey: hasParent ? '2251799813685270' : null,
				rootProcessInstanceKey: hasParent ? '2251799813685260' : null,
			}),
		});

		await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
		const dialog = screen.getByRole('dialog', {name: 'Apply Modifications'});

		await expect.element(dialog.getByText(message, {exact: false})).toBeVisible();
		if (isApplyDisabled) {
			await expect.element(dialog.getByRole('button', {name: 'Apply'})).toBeDisabled();
		} else {
			await expect.element(dialog.getByRole('button', {name: 'Apply'})).toBeEnabled();
		}
		if (hasParent) {
			await expect
				.element(dialog.getByRole('link', {name: 'Root Process - 2251799813685260'}))
				.toHaveAttribute('href', '/operate/processes/2251799813685260');
		}
	});

	it.for([
		{name: 'only the child instance', hierarchy: [PROCESS_INSTANCE_ID]},
		{name: 'the root without the parent', hierarchy: ['2251799813685260', PROCESS_INSTANCE_ID]},
	])(
		'should explain why a child instance cannot be cancelled when the call hierarchy shows $name',
		async ({hierarchy}, {worker}) => {
			worker.use(
				...handlers(),
				mockGetProcessInstanceCallHierarchyEndpoint({
					successResponse: HttpResponse.json(
						hierarchy.map((processInstanceKey) => createCallHierarchy({processInstanceKey})),
					),
				}),
			);
			const screen = await renderModificationPage({
				modifications: [cancelToken({elementId: 'task_1', affectedTokenCount: 1})],
				processInstance: createProcessInstance({
					processInstanceKey: PROCESS_INSTANCE_ID,
					processDefinitionId: 'Process_1',
					parentProcessInstanceKey: '2251799813685270',
					rootProcessInstanceKey: '2251799813685260',
				}),
			});

			await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
			const dialog = screen.getByRole('dialog', {name: 'Apply Modifications'});

			await expect
				.element(dialog.getByText('This instance is a child instance and cannot be canceled entirely.', {exact: false}))
				.toBeVisible();
			await expect.element(dialog.getByRole('link')).not.toBeInTheDocument();
			await expect.element(dialog.getByRole('button', {name: 'Apply'})).toBeDisabled();
		},
	);

	it.for([
		{status: 204, kind: 'success', title: 'Modifications applied', subtitle: undefined},
		{status: 403, kind: 'error', title: 'Modification failed', subtitle: 'You do not have permission'},
		{
			status: 500,
			kind: 'error',
			title: 'Modification failed',
			subtitle: 'Unable to apply modifications, please try again.',
		},
	])(
		'should notify and leave modification mode after a $status response',
		async ({status, kind, title, subtitle}, {worker}) => {
			const expectedPayload = {
				activateInstructions: [
					{elementId: 'task_2', variableInstructions: [{variables: {foo: 'bar'}, scopeId: 'task_2'}]},
				],
				moveInstructions: [],
				terminateInstructions: [{elementInstanceKey: 'instance-1'}],
			};
			worker.use(
				...handlers({statistics: [createProcessDefinitionStatistic({elementId: 'task_1', active: 2})]}),
				mockModifyProcessInstanceEndpoint({
					schema: z.custom((body) => isEqual(body, expectedPayload)),
					successResponse:
						status === 204
							? new HttpResponse(null, {status})
							: HttpResponse.json(createProblemDetails({status}), {status}),
					failureResponse: HttpResponse.json(createProblemDetails({status: 400}), {status: 400}),
				}),
			);
			const screen = await renderModificationPage({
				modifications: [
					addToken({elementId: 'task_2', scopeId: 'scope-1'}),
					variable({operation: 'ADD_VARIABLE', scopeId: 'scope-1', name: 'foo', newValue: '"bar"'}),
					cancelToken({elementId: 'task_1', elementInstanceKey: 'instance-1'}),
				],
			});

			await userEvent.click(screen.getByRole('button', {name: 'Review Modifications'}));
			await userEvent.click(
				screen.getByRole('dialog', {name: 'Apply Modifications'}).getByRole('button', {name: 'Apply'}),
			);

			await expect.poll(() => notificationsStore.notifications[0]?.title).toBe(title);
			await expect.element(screen.getByText('Modification status: disabled, 0 staged')).toBeVisible();
			expect(notificationsStore.notifications[0]?.kind).toBe(kind);
			expect(notificationsStore.notifications[0]?.subtitle).toBe(subtitle);
		},
	);
});
