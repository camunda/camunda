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
import {useParams, useRouterState} from '@tanstack/react-router';
import {HttpResponse, http, delay} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createAgentInstance} from '#/shared-test-modules/api-mocks/agent-instances';
import {createProcessDefinitionStatistic} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
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
import {endpoints} from '#/shared/http/endpoints';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ProcessInstanceContext} from './useProcessInstancePage';
import {processInstanceSearchSchema} from './processInstanceSearch';
import {InstanceDiagram} from './InstanceDiagram';

const INSTANCE_ID = 'instance-1';
const PROCESS_XML = `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
<bpmn:process id="Process_1" isExecutable="false"><bpmn:startEvent id="start_1" />
<bpmn:userTask id="task_1" /><bpmn:callActivity id="call_1" />
<bpmn:businessRuleTask id="rule_1" /><bpmn:endEvent id="end_1" /></bpmn:process>
<bpmndi:BPMNDiagram id="Diagram_1"><bpmndi:BPMNPlane id="Plane_1" bpmnElement="Process_1">
<bpmndi:BPMNShape id="start_di" bpmnElement="start_1"><dc:Bounds x="100" y="100" width="36" height="36" /></bpmndi:BPMNShape><bpmndi:BPMNShape id="task_di" bpmnElement="task_1"><dc:Bounds x="200" y="100" width="100" height="80" /></bpmndi:BPMNShape>
<bpmndi:BPMNShape id="call_di" bpmnElement="call_1"><dc:Bounds x="350" y="100" width="100" height="80" /></bpmndi:BPMNShape><bpmndi:BPMNShape id="rule_di" bpmnElement="rule_1"><dc:Bounds x="500" y="100" width="100" height="80" /></bpmndi:BPMNShape><bpmndi:BPMNShape id="end_di" bpmnElement="end_1"><dc:Bounds x="650" y="100" width="36" height="36" /></bpmndi:BPMNShape>
</bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>`;
const NESTED_XML = PROCESS_XML.replace(
	'<bpmn:endEvent id="end_1" />',
	'<bpmn:subProcess id="sub_1"><bpmn:userTask id="inner_1" /></bpmn:subProcess><bpmn:endEvent id="end_1" />',
).replace(
	'</bpmndi:BPMNDiagram></bpmn:definitions>',
	'<bpmndi:BPMNShape id="sub_di" bpmnElement="sub_1"><dc:Bounds x="750" y="100" width="120" height="100" /></bpmndi:BPMNShape></bpmndi:BPMNDiagram><bpmndi:BPMNDiagram id="Nested_Diagram"><bpmndi:BPMNPlane id="Nested_Plane" bpmnElement="sub_1"><bpmndi:BPMNShape id="inner_di" bpmnElement="inner_1"><dc:Bounds x="770" y="120" width="80" height="60" /></bpmndi:BPMNShape></bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>',
);

const INSTANCE = createProcessInstance({processInstanceKey: INSTANCE_ID, processDefinitionId: 'Process_1'});
const STATISTICS = [
	createProcessDefinitionStatistic({elementId: 'task_1', active: 2, completed: 3, canceled: 1}),
	createProcessDefinitionStatistic({elementId: 'call_1', completed: 1}),
	createProcessDefinitionStatistic({elementId: 'rule_1', completed: 1}),
	createProcessDefinitionStatistic({elementId: 'end_1', completed: 1}),
];

const singleResult = <T,>(item: T) => ({
	items: [item],
	page: {totalItems: 1, startCursor: null, endCursor: null, hasMoreTotalItems: false},
});

function Page(props: React.ComponentProps<typeof InstanceDiagram>) {
	const location = useRouterState({select: (state) => state.location});
	const processInstanceId = useParams({strict: false}).processInstanceId ?? INSTANCE_ID;
	if (!location.pathname.startsWith('/operate/processes/')) {
		return null;
	}
	const search = processInstanceSearchSchema.parse(location.search);
	return (
		<ProcessInstanceContext
			value={{
				processInstanceId,
				processInstance:
					processInstanceId === INSTANCE_ID ? INSTANCE : createProcessInstance({processInstanceKey: processInstanceId}),
				search,
				selection: search,
			}}
		>
			<div style={{width: '900px', height: '400px'}}>
				<InstanceDiagram {...props} />
			</div>
		</ProcessInstanceContext>
	);
}

function renderPage(props: React.ComponentProps<typeof InstanceDiagram> = {}, search = '') {
	return renderWithRouter(() => <Page {...props} />, {
		path: '/operate/processes/$processInstanceId/details',
		initialEntry: `/operate/processes/${INSTANCE_ID}/details${search}`,
	});
}

async function renderLoadedPage(props: React.ComponentProps<typeof InstanceDiagram> = {}, search = '') {
	const screen = await renderPage(props, search);
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

afterEach(async () => {
	await cleanup();
	vi.restoreAllMocks();
	notificationsStore.reset();
});

describe('<InstanceDiagram />', () => {
	it('should download the rendered BPMN definition', async ({worker}) => {
		worker.use(...handlers());
		let filename = '';
		const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
			filename = this.download;
		});
		vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:diagram');
		const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});

		const screen = await renderLoadedPage();
		await userEvent.click(screen.getByRole('button', {name: 'Download XML'}));

		expect(click).toHaveBeenCalledOnce();
		expect(filename).toBe('my-process_v1.bpmn');
		expect(revoke).toHaveBeenCalledWith('blob:diagram');
	});

	it('should show a spinner while loading the XML', async ({worker}) => {
		worker.use(
			http.get(endpoints.getProcessDefinitionXml({processDefinitionKey: '2251799813685279'}).url, async () => {
				await delay(500);
				return HttpResponse.text(PROCESS_XML);
			}),
			...handlers(),
		);

		const screen = await renderPage();

		await expect.element(screen.getByTestId('diagram-spinner')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
	});

	it.for([
		{status: 403, message: 'Missing permissions to view the Definition'},
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

	it.for([
		'',
		'<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" />',
		PROCESS_XML.replace(/<bpmndi:BPMNPlane[\s\S]*?<\/bpmndi:BPMNPlane>/, ''),
	])('should show an empty state when no diagram exists', async (xml, {worker}) => {
		worker.use(...handlers({xml}));

		const screen = await renderPage();

		await expect.element(screen.getByText('No diagram available for this process')).toBeVisible();
	});

	it('should compose state, waiting and agent overlays and refresh when statistics change', async ({worker}) => {
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

		await expect.element(screen.getByTestId('instance-state-task_1-active')).toHaveTextContent('2');
		await expect.element(screen.getByTestId('instance-state-end_1-completedEndEvents')).toBeVisible();
		await expect.element(screen.getByTestId('instance-waiting-task_1')).toHaveTextContent('2 waiting');
		await expect
			.element(screen.getByTestId('instance-agent-call_1'))
			.toHaveTextContent('Thinking... + 1 more active agent');
		await expect.element(screen.getByTestId('instance-agent-shine-call_1')).toBeVisible();
		const shineStyle = getComputedStyle(screen.getByTestId('instance-agent-shine-call_1').element());
		expect(shineStyle.mask).toContain('content-box');
		expect(shineStyle.maskComposite.split(',')[0]).toBe('exclude');
		await expect.element(screen.getByTestId('instance-waiting-call_1')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('instance-waiting-Process_1')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('instance-state-task_1-completed')).not.toBeInTheDocument();

		await screen.queryClient.setQueryData(
			['instanceDiagramStatistics', INSTANCE_ID],
			[createProcessDefinitionStatistic({elementId: 'task_1', active: 5})],
		);

		await expect.element(screen.getByTestId('instance-state-task_1-active')).toHaveTextContent('5');
		await expect.element(screen.getByTestId('instance-state-end_1-completedEndEvents')).not.toBeInTheDocument();

		await screen.queryClient.setQueryData(
			['instanceDiagramAgents', INSTANCE_ID],
			Array.from({length: 3}, (_, index) =>
				createAgentInstance({agentInstanceKey: `agent-${index + 1}`, elementId: 'call_1', status: 'TOOL_CALLING'}),
			),
		);
		await expect
			.element(screen.getByTestId('instance-agent-call_1'))
			.toHaveTextContent('Calling tools... + 2 more active agents');
	});

	it('should measure agent shine against its own diagram when two viewers share an element ID', async ({worker}) => {
		worker.use(...handlers({agents: [createAgentInstance({elementId: 'call_1'})]}));
		await renderLoadedPage();
		worker.use(
			...handlers({
				xml: PROCESS_XML.replace(
					'bpmnElement="call_1"><dc:Bounds x="350" y="100" width="100"',
					'bpmnElement="call_1"><dc:Bounds x="350" y="100" width="140"',
				),
				agents: [createAgentInstance({elementId: 'call_1'})],
			}),
		);
		await renderLoadedPage();

		await expect
			.poll(() =>
				[...document.querySelectorAll<HTMLElement>('[data-testid="instance-agent-shine-call_1"]')].map(
					(shine) => getComputedStyle(shine).width,
				),
			)
			.toEqual(['102px', '142px']);
	});

	it('should show only execution states and pending badges in modification mode', async ({worker}) => {
		const onSelection = vi.fn();
		worker.use(
			...handlers({
				waitStates: [{elementId: 'task_1', waitingCount: 2}],
				agents: [createAgentInstance({elementId: 'call_1'})],
			}),
		);

		const screen = await renderPage({
			isModificationModeEnabled: true,
			isExecutionCountVisible: true,
			onModificationElementSelection: onSelection,
			modificationBadges: [
				{
					elementId: 'task_1',
					type: 'instance-modification',
					position: {top: 0, left: 0},
					payload: {newTokenCount: 2, cancelledTokenCount: 1},
				},
			],
		});

		await expect.element(screen.getByTestId('instance-state-task_1-active')).toBeVisible();
		await expect.element(screen.getByTestId('instance-modification-task_1')).toHaveTextContent('+2 −1');
		await expect.element(screen.getByTestId('instance-state-task_1-completed')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('instance-state-task_1-canceled')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('instance-waiting-task_1')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('instance-agent-call_1')).not.toBeInTheDocument();
		await userEvent.click(document.querySelector<SVGElement>('[data-element-id="task_1"]')!);
		expect(onSelection).not.toHaveBeenCalled();

		await userEvent.dblClick(document.querySelector<SVGElement>('[data-element-id="call_1"]')!);
		expect(screen.router.state.location.pathname).toBe(`/operate/processes/${INSTANCE_ID}/details`);
	});

	it('should synchronize selection and clear the instance key when the selected node is clicked', async ({worker}) => {
		worker.use(...handlers());

		const screen = await renderLoadedPage({}, '?elementId=task_1&elementInstanceKey=instance-2');

		const task = document.querySelector<SVGElement>('[data-element-id="task_1"]');
		expect(task).not.toBeNull();

		await userEvent.click(task!);
		await expect
			.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search).elementInstanceKey)
			.toBeUndefined();
		expect(processInstanceSearchSchema.parse(screen.router.state.location.search).elementId).toBe('task_1');

		await userEvent.click(document.querySelector<HTMLElement>('[data-testid="diagram-canvas"]')!, {
			position: {x: 5, y: 5},
		});
		await expect
			.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search).elementId)
			.toBeUndefined();
	});

	it('should select the URL anchor element and clear the previous instance selection', async ({worker}) => {
		worker.use(...handlers());

		const screen = await renderLoadedPage({}, '?elementId=call_1&anchorElementId=task_1&elementInstanceKey=instance-2');

		await userEvent.click(document.querySelector<SVGElement>('[data-element-id="task_1"]')!);
		await expect
			.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search).elementId)
			.toBe('task_1');
		expect(processInstanceSearchSchema.parse(screen.router.state.location.search).anchorElementId).toBeUndefined();
		expect(processInstanceSearchSchema.parse(screen.router.state.location.search).elementInstanceKey).toBeUndefined();
	});

	it('should clear the previous selection when navigating to a different process instance', async ({worker}) => {
		worker.use(...handlers());

		const screen = await renderLoadedPage({}, '?elementId=task_1');

		await screen.router.navigate({
			to: '/operate/processes/$processInstanceId/details',
			params: {processInstanceId: 'instance-2'},
			search: {elementId: 'task_1'},
		});

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/processes/instance-2/details');
		await expect
			.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search).elementId)
			.toBeUndefined();
	});

	it('should retain a newly selected element when switching to its subprocess root', async ({worker}) => {
		worker.use(
			...handlers({
				xml: NESTED_XML,
				statistics: [...STATISTICS, createProcessDefinitionStatistic({elementId: 'inner_1', active: 1})],
			}),
		);
		const screen = await renderLoadedPage({}, '?elementId=task_1');

		await screen.router.navigate({to: '.', search: {elementId: 'inner_1'}});
		await expect
			.poll(() => document.querySelector('[data-element-id="inner_1"]')?.classList.contains('op-selected'))
			.toBe(true);
		await delay(350);
		expect(processInstanceSearchSchema.parse(screen.router.state.location.search).elementId).toBe('inner_1');

		await userEvent.click(screen.getByRole('link', {name: 'Process_1'}));
		await expect
			.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search).elementId)
			.toBeUndefined();
		await delay(350);
		expect(processInstanceSearchSchema.parse(screen.router.state.location.search).elementId).toBeUndefined();
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

		const screen = await renderLoadedPage({}, '?elementId=task_1');

		await userEvent.dblClick(document.querySelector<SVGElement>('[data-element-id="call_1"]')!);

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/processes/child-1/details');
		expect(processInstanceSearchSchema.parse(screen.router.state.location.search).elementId).toBeUndefined();
	});

	it('should drill into a called decision and skip ambiguous matches', async ({worker}) => {
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
		await userEvent.dblClick(document.querySelector<SVGElement>('[data-element-id="rule_1"]')!);

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/decisions/decision-1');
	});

	it.for([
		'changing process instances',
		'selecting a different element',
		'navigating to a different selection',
		'navigating away with unrelated search',
	] as const)('should ignore a drilldown response after %s', async (action, {worker}) => {
		let release: () => void = () => {};
		let hasRequested = false;
		const delayedResponse = new Promise<void>((resolve) => {
			release = resolve;
		});
		worker.use(
			http.post(
				endpoints.queryElementInstances({filter: {processInstanceKey: INSTANCE_ID}, page: {limit: 1}}).url,
				async () => {
					hasRequested = true;
					await delayedResponse;
					return HttpResponse.json(singleResult({elementInstanceKey: 'element-1'}));
				},
			),
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(singleResult(createProcessInstance({processInstanceKey: 'child-1'}))),
			}),
			...handlers(),
		);
		const screen = await renderLoadedPage();
		await userEvent.dblClick(document.querySelector<SVGElement>('[data-element-id="call_1"]')!);
		await expect.poll(() => hasRequested).toBe(true);

		if (action === 'changing process instances') {
			await screen.router.navigate({
				to: '/operate/processes/$processInstanceId/details',
				params: {processInstanceId: 'instance-2'},
				search: {},
			});
		} else if (action === 'navigating away with unrelated search') {
			screen.router.history.push('/operate/missing?isMultiInstanceBody=invalid');
			await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/missing');
		} else if (action === 'navigating to a different selection') {
			await screen.router.navigate({to: '.', search: {elementId: 'task_1'}});
		} else {
			await userEvent.click(document.querySelector<SVGElement>('[data-element-id="task_1"]')!);
			await expect
				.poll(() => processInstanceSearchSchema.parse(screen.router.state.location.search).elementId)
				.toBe('task_1');
		}
		release();
		await expect
			.poll(
				() => screen.queryClient.getQueryState(['instanceDiagramDrilldownElement', INSTANCE_ID, 'call_1'])?.fetchStatus,
			)
			.toBe('idle');
		await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
		expect(screen.router.state.location.pathname).toBe(
			action === 'changing process instances'
				? '/operate/processes/instance-2/details'
				: action === 'navigating away with unrelated search'
					? '/operate/missing'
					: `/operate/processes/${INSTANCE_ID}/details`,
		);
		if (action === 'navigating away with unrelated search') {
			expect(screen.router.state.location.search).toMatchObject({isMultiInstanceBody: 'invalid'});
		} else {
			expect(processInstanceSearchSchema.parse(screen.router.state.location.search).elementId).toBe(
				action === 'changing process instances' ? undefined : 'task_1',
			);
		}
		if (action === 'selecting a different element' || action === 'navigating to a different selection') {
			expect(screen.queryClient.getQueryData(['instanceDiagramCalledProcess', 'element-1'])).toBeUndefined();
		}
	});
});
