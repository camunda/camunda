/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {useParams, useRouterState} from '@tanstack/react-router';
import {HttpResponse} from 'msw';
import i18n from 'i18next';
import {Toaster, toast} from '@camunda/design-system';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createAgentInstance} from '#/shared-test-modules/api-mocks/agent-instances';
import {createProcessDefinitionStatistic} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {storeSessionState} from '#/shared/browser-storage/session-storage';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessInstanceStatisticsEndpoint,
	mockGetProcessInstanceSequenceFlowsEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockQueryAgentInstancesEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockQueryDecisionInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {Notifications} from '#/shared/notifications/shadcn.components/Notifications';
import {ProcessInstanceContext} from '../useProcessInstancePage';
import {processInstanceSearchSchema} from '../processInstanceSearch';
import {InstanceDiagram} from './InstanceDiagram';

const INSTANCE_ID = 'instance-1';
const PROCESS_XML = `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
<bpmn:process id="Process_1" isExecutable="false"><bpmn:startEvent id="start_1" />
<bpmn:userTask id="task_1" name="Review" /><bpmn:callActivity id="call_1" />
<bpmn:businessRuleTask id="rule_1" /><bpmn:endEvent id="end_1" /></bpmn:process>
<bpmndi:BPMNDiagram id="Diagram_1"><bpmndi:BPMNPlane id="Plane_1" bpmnElement="Process_1">
<bpmndi:BPMNShape id="start_di" bpmnElement="start_1"><dc:Bounds x="100" y="100" width="36" height="36" /></bpmndi:BPMNShape><bpmndi:BPMNShape id="task_di" bpmnElement="task_1"><dc:Bounds x="200" y="100" width="100" height="80" /></bpmndi:BPMNShape>
<bpmndi:BPMNShape id="call_di" bpmnElement="call_1"><dc:Bounds x="350" y="100" width="100" height="80" /></bpmndi:BPMNShape><bpmndi:BPMNShape id="rule_di" bpmnElement="rule_1"><dc:Bounds x="500" y="100" width="100" height="80" /></bpmndi:BPMNShape><bpmndi:BPMNShape id="end_di" bpmnElement="end_1"><dc:Bounds x="650" y="100" width="36" height="36" /></bpmndi:BPMNShape>
</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>`;

const STATISTICS = [
	createProcessDefinitionStatistic({elementId: 'task_1', active: 2, completed: 3, incidents: 1}),
	createProcessDefinitionStatistic({elementId: 'call_1', completed: 1}),
	createProcessDefinitionStatistic({elementId: 'rule_1', completed: 1}),
	createProcessDefinitionStatistic({elementId: 'end_1', completed: 1}),
];

const singleResult = <T,>(item: T) => ({
	items: [item],
	page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
});

function Page() {
	const location = useRouterState({select: (state) => state.location});
	const processInstanceId = useParams({strict: false}).processInstanceId!;
	const search = processInstanceSearchSchema.parse(location.search);
	const processInstance = createProcessInstance({
		processInstanceKey: processInstanceId,
		processDefinitionId: 'Process_1',
	});

	return (
		<ProcessInstanceContext value={{processInstanceId, processInstance, search, selection: search}}>
			<div style={{width: '900px', height: '400px'}}>
				<InstanceDiagram />
			</div>
			<Toaster />
			<Notifications />
		</ProcessInstanceContext>
	);
}

function renderPage(search = '') {
	return renderWithRouter(() => <Page />, {
		path: '/operate-preview/processes/$processInstanceId/details',
		initialEntry: `/operate-preview/processes/${INSTANCE_ID}/details${search}`,
	});
}

async function renderLoadedPage(search = '') {
	const screen = await renderPage(search);
	await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
	return screen;
}

function handlers({
	xml = PROCESS_XML,
	statistics = STATISTICS,
	agents = [],
	waitStates = [],
}: {
	xml?: string;
	statistics?: typeof STATISTICS;
	agents?: ReturnType<typeof createAgentInstance>[];
	waitStates?: {elementId: string; waitingCount: number}[];
} = {}) {
	return [
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(xml)}),
		mockGetProcessInstanceStatisticsEndpoint({successResponse: HttpResponse.json({items: statistics})}),
		mockGetProcessInstanceSequenceFlowsEndpoint({successResponse: HttpResponse.json({items: []})}),
		mockGetProcessInstanceWaitStateStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: waitStates})),
		}),
		mockQueryAgentInstancesEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: agents})),
		}),
	] as const;
}

beforeEach(() => {
	storeSessionState('clientConfig', createSystemConfiguration());
});

afterEach(async () => {
	await cleanup();
	await i18n.changeLanguage('en');
	notificationsStore.reset();
	toast.dismiss();
});

describe('<InstanceDiagram /> (DS)', () => {
	it('should offer the fullscreen control next to the zoom controls', async ({worker}) => {
		worker.use(...handlers());

		const screen = await renderLoadedPage();

		await expect.element(screen.getByRole('button', {name: 'Zoom in diagram'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Enter fullscreen'})).toBeVisible();
	});

	it('should show a spinner while loading the XML', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(PROCESS_XML), delay: 'infinite'}),
			...handlers(),
		);

		const screen = await renderPage();

		await expect.element(screen.getByRole('status', {name: 'Loading diagram'})).toBeVisible();
	});

	it.for([
		{status: 403, message: 'You do not have permission to view this information'},
		{status: 500, message: "Couldn't fetch data"},
	])('should render the XML error state for $status', async ({status, message}, {worker}) => {
		worker.use(
			mockGetProcessDefinitionXmlEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status}), {status}),
			}),
			...handlers(),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText(message)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).not.toBeInTheDocument();
	});

	it('should show an empty state when no diagram exists', async ({worker}) => {
		worker.use(...handlers({xml: PROCESS_XML.replace(/<bpmndi:BPMNPlane[\s\S]*?<\/bpmndi:BPMNPlane>/, '')}));

		const screen = await renderPage();

		await expect.element(screen.getByText('No diagram available for this process')).toBeVisible();
	});

	it('should render state, waiting, and agent overlays', async ({worker}) => {
		worker.use(
			...handlers({
				waitStates: [
					{elementId: 'task_1', waitingCount: 2},
					{elementId: 'call_1', waitingCount: 1},
					{elementId: 'Process_1', waitingCount: 1},
				],
				agents: [
					createAgentInstance({elementId: 'call_1'}),
					createAgentInstance({agentInstanceKey: 'agent-2', elementId: 'call_1'}),
				],
			}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByRole('img', {name: 'Review, Active: 2'})).toHaveTextContent('2');
		await expect.element(screen.getByRole('img', {name: 'Review, Incidents: 1'})).toBeVisible();
		await expect.element(screen.getByRole('img', {name: 'Completed: 1', exact: true})).toBeVisible();
		await expect.element(screen.getByText('2 waiting')).toBeVisible();
		await expect.element(screen.getByText('Thinking... + 1 more active agent')).toBeVisible();
		await expect.element(screen.getByText('Waiting', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('img', {name: 'Review, Completed: 3'})).not.toBeInTheDocument();
	});

	it('should not show waiting overlays when the wait-states flag is off', async ({worker}) => {
		storeSessionState(
			'clientConfig',
			createSystemConfiguration({deployment: {...createSystemConfiguration().deployment, isWaitStatesEnabled: false}}),
		);
		worker.use(...handlers({waitStates: [{elementId: 'task_1', waitingCount: 2}]}));

		const screen = await renderPage();

		await expect.element(screen.getByRole('img', {name: 'Review, Active: 2'})).toBeVisible();
		await expect.element(screen.getByText('2 waiting')).not.toBeInTheDocument();
	});

	it('should synchronize selection and clear the instance key when the selected node is clicked', async ({worker}) => {
		worker.use(...handlers());

		const screen = await renderLoadedPage('?elementId=task_1&elementInstanceKey=instance-2');
		const reviewTask = screen.getByRole('button', {name: 'Review (user task)'});
		await expect.element(reviewTask).toHaveAttribute('aria-pressed', 'true');

		await userEvent.click(reviewTask);
		await expect
			.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search).elementInstanceKey)
			.toBeUndefined();
		expect(processInstanceSearchSchema.parse(screen.router.state.location.search).elementId).toBe('task_1');

		await userEvent.click(screen.getByRole('region', {name: 'Diagram'}), {position: {x: 5, y: 5}});
		await expect
			.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search).elementId)
			.toBeUndefined();
	});

	it('should drill into a called process without inheriting parent selection', async ({worker}) => {
		worker.use(
			...handlers(),
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(singleResult({elementInstanceKey: 'element-1'})),
			}),
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(singleResult(createProcessInstance({processInstanceKey: 'child-1'}))),
			}),
		);

		const screen = await renderLoadedPage('?elementId=task_1');

		await userEvent.dblClick(screen.getByRole('button', {name: 'call activity'}));

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate-preview/processes/child-1/details');
		expect(screen.router.state.location.search).toEqual({});
	});

	it('should drill into a called decision', async ({worker}) => {
		worker.use(
			...handlers(),
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(singleResult({elementInstanceKey: 'element-2'})),
			}),
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(singleResult({decisionEvaluationInstanceKey: 'decision-1'})),
			}),
		);

		const screen = await renderLoadedPage();
		await userEvent.dblClick(screen.getByRole('button', {name: 'business rule task'}));

		await expect.element(screen.getByRole('region', {name: 'Diagram'})).not.toBeInTheDocument();
		expect(screen.router.state.location.pathname).toMatch(/\/decisions\/decision-1$/);
	});

	it('should notify when the called process cannot be resolved', async ({worker}) => {
		worker.use(
			...handlers(),
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);

		const screen = await renderLoadedPage();
		await userEvent.dblClick(screen.getByRole('button', {name: 'call activity'}));

		await expect.element(screen.getByText('Failed to resolve called instances')).toBeVisible();
	});

	it('should cancel a pending drilldown when another element is selected', async ({worker}) => {
		worker.use(
			...handlers(),
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(singleResult({elementInstanceKey: 'element-1'})),
				delay: 'infinite',
			}),
		);

		const screen = await renderLoadedPage();
		const callActivity = screen.getByRole('button', {name: 'call activity'});
		await userEvent.dblClick(callActivity);
		await expect.element(callActivity).toHaveAttribute('aria-busy', 'true');
		await userEvent.click(screen.getByRole('button', {name: 'Review (user task)'}));

		await expect.element(callActivity).not.toHaveAttribute('aria-busy');
	});
});
