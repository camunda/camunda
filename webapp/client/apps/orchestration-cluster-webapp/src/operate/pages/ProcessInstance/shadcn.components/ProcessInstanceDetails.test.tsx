/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {cleanup} from 'vitest-browser-react';
import {http, HttpResponse} from 'msw';
import {z} from 'zod';
import {useRouterState} from '@tanstack/react-router';
import {endpoints, type ElementInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	createAgentInstance,
	createQueryAgentInstanceHistoryResponse,
	createQueryAgentInstancesResponse,
} from '#/shared-test-modules/api-mocks/agent-instances';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetElementInstanceEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryElementInstanceInspectionEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockQueryAgentInstancesEndpoint,
	mockQueryAgentInstanceHistoryEndpoint,
	mockQueryJobsEndpoint,
	mockQueryMessageSubscriptionsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockQueryUserTasksEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {
	createElementInstanceInspection,
	createQueryElementInstanceInspectionResponse,
} from '#/shared-test-modules/api-mocks/element-instance-inspection';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createJob, createQueryJobsResponse} from '#/shared-test-modules/api-mocks/jobs';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {
	createMessageSubscription,
	createQueryMessageSubscriptionsResponse,
} from '#/shared-test-modules/api-mocks/message-subscriptions';
import {ProcessInstancePageProvider} from '../ProcessInstancePageProvider';
import {processInstanceSearchSchema} from '../processInstanceSearch';
import {ProcessInstanceDetails} from './ProcessInstanceDetails';

const PROCESS_INSTANCE_ID = '2251799813685280';
const ELEMENT_INSTANCE_KEY = '2251799813685281';

const BPMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="my-process" isExecutable="true">
    <bpmn:serviceTask id="service-task" />
    <bpmn:userTask id="camunda-task"><bpmn:extensionElements><zeebe:userTask /></bpmn:extensionElements></bpmn:userTask>
    <bpmn:userTask id="worker-task" />
    <bpmn:callActivity id="call-activity" />
    <bpmn:businessRuleTask id="rule-task" />
    <bpmn:intermediateCatchEvent id="message-event"><bpmn:messageEventDefinition /></bpmn:intermediateCatchEvent>
  </bpmn:process>
</bpmn:definitions>`;

afterEach(async () => {
	await cleanup();
	sessionStorage.clear();
});

function renderDetails(search = '') {
	sessionStorage.setItem(
		'clientConfig',
		JSON.stringify(createSystemConfiguration({components: {active: ['operate', 'tasklist']}})),
	);

	function Details() {
		const routeSearch = useRouterState({select: (state) => state.location.search});
		return (
			<div style={{height: '600px', display: 'flex', flexDirection: 'column'}}>
				<ProcessInstancePageProvider
					processInstanceId={PROCESS_INSTANCE_ID}
					processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, state: 'ACTIVE'})}
					search={processInstanceSearchSchema.parse(routeSearch)}
				>
					<ProcessInstanceDetails />
				</ProcessInstancePageProvider>
			</div>
		);
	}

	return renderWithRouter(Details, {
		path: '/operate-preview/processes/$processInstanceId/details',
		initialEntry: `/operate-preview/processes/${PROCESS_INSTANCE_ID}/details${search}`,
	});
}

function handlers(elementInstance: ElementInstance = instanceOf('service-task', 'SERVICE_TASK')) {
	return [
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
		mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
		mockQueryJobsEndpoint({successResponse: HttpResponse.json(createQueryJobsResponse())}),
		mockQueryElementInstanceInspectionEndpoint({
			successResponse: HttpResponse.json(createQueryElementInstanceInspectionResponse()),
		}),
		mockQueryAgentInstancesEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse()),
		}),
	];
}

function instanceOf(elementId: string, type: ElementInstance['type']) {
	return createElementInstance({elementInstanceKey: ELEMENT_INSTANCE_KEY, elementId, type});
}

const selected = (elementId: string) => `?elementId=${elementId}&elementInstanceKey=${ELEMENT_INSTANCE_KEY}`;
const emptyAgentResponse = () => HttpResponse.json(createQueryAgentInstancesResponse());
const agentsResponse = () =>
	HttpResponse.json(createQueryAgentInstancesResponse([createAgentInstance({status: 'TOOL_CALLING'})]));
const agentRequestSchema = (elementInstanceKeyFilter: z.ZodRawShape = {}) =>
	z.object({
		sort: z.tuple([z.object({field: z.literal('creationDate'), order: z.literal('ASC')})]),
		page: z.object({limit: z.literal(50)}).strict(),
		filter: z
			.object({
				processInstanceKey: z.literal(PROCESS_INSTANCE_ID),
				elementId: z.literal('service-task'),
				...elementInstanceKeyFilter,
			})
			.strict(),
	});

describe('<ProcessInstanceDetails /> (design system)', () => {
	it('should show the wait state of the process instance', async ({worker}) => {
		worker.use(
			mockQueryElementInstanceInspectionEndpoint({
				successResponse: HttpResponse.json(
					createQueryElementInstanceInspectionResponse([
						createElementInstanceInspection({elementInstanceKey: PROCESS_INSTANCE_ID}),
					]),
				),
			}),
			...handlers(),
		);
		const screen = await renderDetails();

		await expect.element(screen.getByRole('heading', {name: 'Status'})).toBeVisible();
		await expect.element(screen.getByText('Waiting for message: payment-received')).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Element Instance'})).not.toBeInTheDocument();
	});

	it.for([
		{totalItems: 0, message: 'No element selected'},
		{totalItems: 3, message: 'Select a single element instance'},
	])('should show "$message" when $totalItems element instances match', async ({totalItems, message}, {worker}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([], totalItems)),
			}),
			...handlers(),
		);
		const screen = await renderDetails('?elementId=service-task');

		await expect.element(screen.getByText(message)).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Element Instance'})).not.toBeInTheDocument();
	});

	it('should show the element instance, its job and its wait state', async ({worker}) => {
		worker.use(
			mockQueryJobsEndpoint({
				successResponse: HttpResponse.json(
					createQueryJobsResponse([createJob({retries: 2, priority: 7, type: 'send-email', worker: 'email-worker'})]),
				),
			}),
			mockQueryElementInstanceInspectionEndpoint({
				successResponse: HttpResponse.json(
					createQueryElementInstanceInspectionResponse([
						createElementInstanceInspection({
							elementInstanceKey: ELEMENT_INSTANCE_KEY,
							details: {waitStateType: 'SIGNAL', signalName: 'go'},
						}),
					]),
				),
			}),
			...handlers(),
		);
		const screen = await renderDetails(selected('service-task'));

		await expect.element(screen.getByText('Waiting for signal: go')).toBeVisible();
		await expect.element(screen.getByRole('row', {name: `Element Instance Key ${ELEMENT_INSTANCE_KEY}`})).toBeVisible();
		await expect.element(screen.getByRole('row', {name: 'Retries Left 2'})).toBeVisible();
		await expect.element(screen.getByRole('row', {name: 'Job Priority 7'})).toBeVisible();
		await expect.element(screen.getByRole('row', {name: 'Job Type send-email'})).toBeVisible();
		await expect.element(screen.getByRole('row', {name: 'Worker email-worker'})).toBeVisible();
		await expect.element(screen.getByText('Called Process Instance')).not.toBeInTheDocument();
	});

	it('should link to the called process instance of a call activity', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '2251799813685777', processDefinitionName: 'Child'})],
					}),
				),
			}),
			...handlers(instanceOf('call-activity', 'CALL_ACTIVITY')),
		);
		const screen = await renderDetails(selected('call-activity'));

		await expect.element(screen.getByRole('link', {name: 'View Child instance 2251799813685777'})).toBeVisible();
	});

	it('should link to the decision instance evaluated by a business rule task', async ({worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [createDecisionInstance()]})),
			}),
			...handlers(instanceOf('rule-task', 'BUSINESS_RULE_TASK')),
		);
		const screen = await renderDetails(selected('rule-task'));

		const link = screen.getByRole('link', {name: 'View Invoice Classification instance 123567'});
		await expect.element(link).toBeVisible();
		await expect.element(link).toHaveAttribute('href', '/operate/decisions/123567');
	});

	it('should show an error instead of the empty message when the element instance fails to load', async ({worker}) => {
		worker.use(
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
			...handlers(),
		);
		const screen = await renderDetails(selected('service-task'));

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(screen.getByText('No element selected')).not.toBeInTheDocument();
	});

	it('should render the agent error state when loading agent instances fails', async ({worker}) => {
		worker.use(
			mockQueryElementInstanceInspectionEndpoint({
				successResponse: HttpResponse.json(
					createQueryElementInstanceInspectionResponse([
						createElementInstanceInspection({elementInstanceKey: ELEMENT_INSTANCE_KEY}),
					]),
				),
			}),
			mockQueryAgentInstancesEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
			...handlers(),
		);
		const screen = await renderDetails(selected('service-task'));

		await expect.element(screen.getByRole('heading', {name: 'Status'})).toBeVisible();
		await expect.element(screen.getByText('Waiting for message: payment-received')).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: 'Agent Instance'})).toBeVisible();
		await expect.element(screen.getByText('Unable to load agent information.')).toBeVisible();
		await expect.element(screen.getByText('No element selected')).not.toBeInTheDocument();
	});

	it('should keep waiting status visible when agent polling fails after an empty result', async ({worker}) => {
		let failedAgentRequests = 0;
		worker.use(
			mockQueryElementInstanceInspectionEndpoint({
				successResponse: HttpResponse.json(
					createQueryElementInstanceInspectionResponse([
						createElementInstanceInspection({elementInstanceKey: ELEMENT_INSTANCE_KEY}),
					]),
				),
			}),
			mockQueryAgentInstancesEndpoint({
				successResponse: emptyAgentResponse(),
				once: true,
			}),
			http.post(endpoints.queryAgentInstances.getUrl(), () => {
				failedAgentRequests += 1;
				return HttpResponse.json(createProblemDetails({status: 500}), {status: 500});
			}),
			...handlers(),
		);
		const screen = await renderDetails(selected('service-task'));

		await expect.element(screen.getByRole('heading', {name: 'Status'})).toBeVisible();
		await expect.element(screen.getByText('Waiting for message: payment-received')).toBeVisible();
		await expect.element(screen.getByText('Unable to load agent information.')).not.toBeInTheDocument();
		await expect.poll(() => failedAgentRequests, {timeout: 7000}).toBeGreaterThan(0);
		await expect.element(screen.getByRole('heading', {name: 'Status'})).toBeVisible();
		await expect.element(screen.getByText('Waiting for message: payment-received')).toBeVisible();
		await expect.element(screen.getByText('Unable to load agent information.')).not.toBeInTheDocument();
	});

	it('should filter on the element id only for a multi-instance body', async ({worker}) => {
		worker.use(
			mockQueryAgentInstancesEndpoint({
				schema: agentRequestSchema(),
				successResponse: agentsResponse(),
				failureResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
				once: true,
			}),
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(instanceOf('service-task', 'MULTI_INSTANCE_BODY')),
				delay: 250,
			}),
			mockQueryAgentInstanceHistoryEndpoint({
				successResponse: HttpResponse.json(createQueryAgentInstanceHistoryResponse()),
			}),
			...handlers(),
		);
		const screen = await renderDetails(`${selected('service-task')}&isMultiInstanceBody=true`);

		await expect.element(screen.getByRole('heading', {name: 'Agent Instance'})).toBeVisible();
		await expect.element(screen.getByText('Status: Calling tools')).toBeVisible();
	});

	it('should filter on the element id only for a multi-instance body selected by its key', async ({worker}) => {
		worker.use(
			mockQueryAgentInstancesEndpoint({
				schema: agentRequestSchema(),
				successResponse: agentsResponse(),
				failureResponse: emptyAgentResponse(),
			}),
			mockQueryAgentInstanceHistoryEndpoint({
				successResponse: HttpResponse.json(createQueryAgentInstanceHistoryResponse()),
			}),
			...handlers(instanceOf('service-task', 'MULTI_INSTANCE_BODY')),
		);
		const screen = await renderDetails(`?elementInstanceKey=${ELEMENT_INSTANCE_KEY}`);

		await expect.element(screen.getByRole('heading', {name: 'Agent Instance'})).toBeVisible();
		await expect.element(screen.getByText('Status: Calling tools')).toBeVisible();
	});

	it('should show the single-instance selection hint with agent details for multiple element matches', async ({
		worker,
	}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([], 3)),
			}),
			mockQueryAgentInstancesEndpoint({
				successResponse: agentsResponse(),
			}),
			mockQueryAgentInstanceHistoryEndpoint({
				successResponse: HttpResponse.json(createQueryAgentInstanceHistoryResponse()),
			}),
			...handlers(),
		);
		const screen = await renderDetails('?elementId=service-task');

		await expect.element(screen.getByRole('heading', {name: 'Agent Instance'})).toBeVisible();
		await expect.element(screen.getByText('Select a single element instance')).toBeVisible();
	});

	it('should show the user task properties and a Tasklist link for a Camunda user task', async ({worker}) => {
		worker.use(
			mockQueryUserTasksEndpoint({
				successResponse: HttpResponse.json(
					createQueryUserTasksResponse({
						items: [createUserTask({assignee: 'demo', candidateGroups: ['sales', 'ops'], priority: 75})],
					}),
				),
			}),
			...handlers(instanceOf('camunda-task', 'USER_TASK')),
		);
		const screen = await renderDetails(selected('camunda-task'));

		await expect.element(screen.getByRole('link', {name: 'Open Tasklist'})).toBeVisible();
		await expect.element(screen.getByRole('row', {name: 'Assignee demo'})).toBeVisible();
		await expect.element(screen.getByRole('row', {name: 'Candidate Groups sales, ops'})).toBeVisible();
		await expect.element(screen.getByRole('row', {name: 'Priority 75'})).toBeVisible();
	});

	it('should warn about deprecated job worker user tasks', async ({worker}) => {
		worker.use(...handlers(instanceOf('worker-task', 'USER_TASK')));
		const screen = await renderDetails(selected('worker-task'));

		await expect.element(screen.getByText('User tasks with job worker implementation are deprecated.')).toBeVisible();
		await expect.element(screen.getByText('Consider migrating to Camunda user tasks.')).toBeVisible();
		await expect.element(screen.getByText('Assignee')).not.toBeInTheDocument();
	});

	it('should show the message subscription of a message event', async ({worker}) => {
		worker.use(
			mockQueryMessageSubscriptionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryMessageSubscriptionsResponse({
						items: [createMessageSubscription({messageName: 'payment-received', correlationKey: 'order-1'})],
					}),
				),
			}),
			...handlers(instanceOf('message-event', 'INTERMEDIATE_CATCH_EVENT')),
		);
		const screen = await renderDetails(selected('message-event'));

		await expect.element(screen.getByRole('row', {name: 'Message Name payment-received'})).toBeVisible();
		await expect.element(screen.getByRole('row', {name: 'Correlation Key order-1'})).toBeVisible();
		await expect.element(screen.getByRole('row', {name: 'Subscription State CREATED'})).toBeVisible();
	});
});
