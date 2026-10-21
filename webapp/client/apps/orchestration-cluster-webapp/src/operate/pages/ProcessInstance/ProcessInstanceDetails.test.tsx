/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {useNavigate, useRouterState} from '@tanstack/react-router';
import type {
	ElementInstance,
	ProcessInstance,
	GetSystemConfigurationResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createAgentInstance, createQueryAgentInstancesResponse} from '#/shared-test-modules/api-mocks/agent-instances';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetElementInstanceEndpoint,
	mockQueryAgentInstancesEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockQueryElementInstanceInspectionEndpoint,
	mockQueryElementInstanceInspectionByFilterEndpoint,
	mockQueryElementInstancesEndpoint,
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
import {createJob, createQueryJobsResponse} from '#/shared-test-modules/api-mocks/jobs';
import {createQueryUserTasksResponse, createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {
	createMessageSubscription,
	createQueryMessageSubscriptionsResponse,
} from '#/shared-test-modules/api-mocks/message-subscriptions';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {ProcessInstanceDetails} from './ProcessInstanceDetails';
import {processInstanceSearchSchema} from './processInstanceSearch';

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

function renderDetails({
	search = '',
	processInstance = createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, state: 'ACTIVE'}),
	activeComponents = ['operate', 'tasklist'],
	isMultiTenancyEnabled = false,
}: {
	search?: string;
	processInstance?: ProcessInstance;
	activeComponents?: GetSystemConfigurationResponseBody['components']['active'];
	isMultiTenancyEnabled?: boolean;
} = {}) {
	sessionStorage.setItem(
		'clientConfig',
		JSON.stringify(
			createSystemConfiguration({
				components: {active: activeComponents},
				deployment: {isMultiTenancyEnabled, isTenantsApiEnabled: false, maxRequestSize: 0},
			}),
		),
	);

	function Details() {
		const routeSearch = useRouterState({select: (state) => state.location.search});
		const navigate = useNavigate();
		return (
			<>
				<button
					type="button"
					onClick={() => void navigate({to: '.', search: {elementInstanceKey: '2251799813685999'}})}
				>
					select other instance
				</button>
				<button
					type="button"
					onClick={() =>
						void navigate({to: '.', search: {elementId: 'service-task', elementInstanceKey: ELEMENT_INSTANCE_KEY}})
					}
				>
					select element
				</button>
				<button type="button" onClick={() => void navigate({to: '.', search: {}})}>
					select process
				</button>
				<ProcessInstancePageProvider
					processInstanceId={PROCESS_INSTANCE_ID}
					processInstance={processInstance}
					search={processInstanceSearchSchema.parse(routeSearch)}
				>
					<ProcessInstanceDetails />
				</ProcessInstancePageProvider>
			</>
		);
	}

	return renderWithRouter(Details, {
		path: '/operate/processes/$processInstanceId/details',
		initialEntry: `/operate/processes/${PROCESS_INSTANCE_ID}/details${search}`,
	});
}

function instanceOf(elementId: string, type: ElementInstance['type']) {
	return createElementInstance({elementInstanceKey: ELEMENT_INSTANCE_KEY, elementId, type});
}

function handlers({
	elementInstance = createElementInstance({elementInstanceKey: ELEMENT_INSTANCE_KEY, elementId: 'service-task'}),
}: {elementInstance?: ReturnType<typeof createElementInstance>} = {}) {
	return [
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
		mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
		mockQueryJobsEndpoint({successResponse: HttpResponse.json(createQueryJobsResponse())}),
		mockQueryAgentInstancesEndpoint({successResponse: HttpResponse.json(createQueryAgentInstancesResponse())}),
		mockQueryElementInstanceInspectionEndpoint({
			successResponse: HttpResponse.json(createQueryElementInstanceInspectionResponse()),
		}),
	];
}

const selected = (elementId: string) => `?elementId=${elementId}&elementInstanceKey=${ELEMENT_INSTANCE_KEY}`;

describe('<ProcessInstanceDetails />', () => {
	it('should show the process scope wait state', async ({worker}) => {
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

		await expect.element(screen.getByText('Waiting for message: payment-received')).toBeVisible();
		await expect.element(screen.getByText('Element Instance')).not.toBeInTheDocument();
	});

	it('should ask for a selection when no element instance exists', async ({worker}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({successResponse: HttpResponse.json(createQueryElementInstancesResponse([]))}),
			...handlers(),
		);
		const screen = await renderDetails({search: '?elementId=service-task'});

		await expect.element(screen.getByText('There is no element selected.')).toBeVisible();
	});

	it('should ask for a single instance when several are selected', async ({worker}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([], 3)),
			}),
			...handlers(),
		);
		const screen = await renderDetails({search: '?elementId=service-task'});

		await expect
			.element(screen.getByText('To view the details, select a single element instance in the instance history.'))
			.toBeVisible();
	});

	it('should show the element instance key and the execution duration', async ({worker}) => {
		worker.use(
			...handlers({
				elementInstance: createElementInstance({
					elementInstanceKey: ELEMENT_INSTANCE_KEY,
					elementId: 'service-task',
					state: 'COMPLETED',
					startDate: '2026-01-15T10:00:00.000Z',
					endDate: '2026-01-15T10:00:30.000Z',
				}),
			}),
		);
		const screen = await renderDetails({search: selected('service-task')});

		await expect.element(screen.getByText(ELEMENT_INSTANCE_KEY)).toBeVisible();
		await expect.element(screen.getByText('30 seconds')).toBeVisible();
	});

	it('should show the job fields of the element instance', async ({worker}) => {
		worker.use(
			mockQueryJobsEndpoint({
				successResponse: HttpResponse.json(
					createQueryJobsResponse([createJob({retries: 2, priority: 7, type: 'send-email', worker: 'email-worker'})]),
				),
			}),
			...handlers(),
		);
		const screen = await renderDetails({search: selected('service-task')});

		await expect.element(screen.getByTestId('retries-left-count')).toHaveTextContent('2');
		await expect.element(screen.getByTestId('job-priority')).toHaveTextContent('7');
		await expect.element(screen.getByText('send-email')).toBeVisible();
		await expect.element(screen.getByText('email-worker')).toBeVisible();
	});

	it('should show the wait state of an active element instance', async ({worker}) => {
		worker.use(
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
		const screen = await renderDetails({search: selected('service-task')});

		await expect.element(screen.getByText('Waiting for signal: go')).toBeVisible();
	});

	it('should not show a wait state for a completed element instance', async ({worker}) => {
		worker.use(
			mockQueryElementInstanceInspectionEndpoint({
				successResponse: HttpResponse.json(
					createQueryElementInstanceInspectionResponse([
						createElementInstanceInspection({elementInstanceKey: ELEMENT_INSTANCE_KEY}),
					]),
				),
			}),
			...handlers({
				elementInstance: createElementInstance({
					elementInstanceKey: ELEMENT_INSTANCE_KEY,
					elementId: 'service-task',
					state: 'COMPLETED',
					endDate: '2026-01-15T10:00:30.000Z',
				}),
			}),
		);
		const screen = await renderDetails({search: selected('service-task')});

		await expect.element(screen.getByText('Element Instance Key')).toBeVisible();
		await expect.element(screen.getByText(/Waiting for message/)).not.toBeInTheDocument();
	});

	it('should show the element instance when wait states cannot be fetched', async ({worker}) => {
		worker.use(
			mockQueryElementInstanceInspectionEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
			...handlers(),
		);
		const screen = await renderDetails({search: selected('service-task')});

		await expect.element(screen.getByText('Element Instance Key')).toBeVisible();
	});

	describe('agent instances', () => {
		const agentsResponse = () =>
			HttpResponse.json(createQueryAgentInstancesResponse([createAgentInstance({status: 'TOOL_CALLING'})]));
		const emptyResponse = () => HttpResponse.json(createQueryAgentInstancesResponse());
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

		it('should show the agent details of the selected element instance instead of its wait state', async ({worker}) => {
			worker.use(
				mockQueryAgentInstancesEndpoint({
					schema: agentRequestSchema({elementInstanceKeys: z.tuple([z.literal(ELEMENT_INSTANCE_KEY)])}),
					successResponse: agentsResponse(),
					failureResponse: emptyResponse(),
				}),
				mockQueryElementInstanceInspectionEndpoint({
					successResponse: HttpResponse.json(
						createQueryElementInstanceInspectionResponse([
							createElementInstanceInspection({elementInstanceKey: ELEMENT_INSTANCE_KEY}),
						]),
					),
				}),
				...handlers(),
			);
			const screen = await renderDetails({search: selected('service-task')});

			await expect.element(screen.getByRole('heading', {name: 'Agent Instance'})).toBeVisible();
			await expect.element(screen.getByText('Status: Calling tools')).toBeVisible();
			await expect.element(screen.getByRole('heading', {name: 'Element Instance'})).toBeVisible();
			await expect.element(screen.getByText('Waiting for message: payment-received')).not.toBeInTheDocument();
		});

		it('should show the agent details when only an element instance key is selected', async ({worker}) => {
			worker.use(
				mockQueryAgentInstancesEndpoint({
					schema: agentRequestSchema({elementInstanceKeys: z.tuple([z.literal(ELEMENT_INSTANCE_KEY)])}),
					successResponse: agentsResponse(),
					failureResponse: emptyResponse(),
				}),
				...handlers(),
			);
			const screen = await renderDetails({search: `?elementInstanceKey=${ELEMENT_INSTANCE_KEY}`});

			await expect.element(screen.getByRole('heading', {name: 'Agent Instance'})).toBeVisible();
			await expect.element(screen.getByText('Status: Calling tools')).toBeVisible();
		});

		it('should filter on the element id only for a multi-instance body', async ({worker}) => {
			worker.use(
				mockQueryAgentInstancesEndpoint({
					schema: agentRequestSchema(),
					successResponse: agentsResponse(),
					failureResponse: emptyResponse(),
				}),
				...handlers({
					elementInstance: createElementInstance({
						elementInstanceKey: ELEMENT_INSTANCE_KEY,
						elementId: 'service-task',
						type: 'MULTI_INSTANCE_BODY',
					}),
				}),
			);
			const screen = await renderDetails({search: `${selected('service-task')}&isMultiInstanceBody=true`});

			await expect.element(screen.getByRole('heading', {name: 'Agent Instance'})).toBeVisible();
			await expect.element(screen.getByText(ELEMENT_INSTANCE_KEY)).toBeVisible();
		});

		it('should filter on the element id only for a multi-instance body selected by its key', async ({worker}) => {
			worker.use(
				mockQueryAgentInstancesEndpoint({
					schema: agentRequestSchema(),
					successResponse: agentsResponse(),
					failureResponse: emptyResponse(),
				}),
				...handlers({
					elementInstance: createElementInstance({
						elementInstanceKey: ELEMENT_INSTANCE_KEY,
						elementId: 'service-task',
						type: 'MULTI_INSTANCE_BODY',
					}),
				}),
			);
			const screen = await renderDetails({search: `?elementInstanceKey=${ELEMENT_INSTANCE_KEY}`});

			await expect.element(screen.getByRole('heading', {name: 'Agent Instance'})).toBeVisible();
		});

		it('should show the agent details when several element instances are selected', async ({worker}) => {
			worker.use(
				mockQueryElementInstancesEndpoint({
					successResponse: HttpResponse.json(createQueryElementInstancesResponse([], 3)),
				}),
				mockQueryAgentInstancesEndpoint({
					schema: agentRequestSchema(),
					successResponse: agentsResponse(),
					failureResponse: emptyResponse(),
				}),
				...handlers(),
			);
			const screen = await renderDetails({search: '?elementId=service-task'});

			await expect.element(screen.getByRole('heading', {name: 'Agent Instance'})).toBeVisible();
			await expect.element(screen.getByRole('heading', {name: 'Element Instance'})).toBeVisible();
			await expect
				.element(screen.getByText('To view the details, select a single element instance in the instance history.'))
				.toBeVisible();
		});

		it('should show an error hint when the agent instances cannot be fetched', async ({worker}) => {
			worker.use(
				mockQueryAgentInstancesEndpoint({
					successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
				}),
				...handlers(),
			);
			const screen = await renderDetails({search: selected('service-task')});

			await expect.element(screen.getByText('Unable to load agent information.')).toBeVisible();
			await expect.element(screen.getByText('Element Instance Key')).toBeVisible();
		});

		it('should show the wait state instead of agent details when the element has no agent instances', async ({
			worker,
		}) => {
			worker.use(
				mockQueryAgentInstancesEndpoint({
					schema: agentRequestSchema({elementInstanceKeys: z.tuple([z.literal(ELEMENT_INSTANCE_KEY)])}),
					successResponse: emptyResponse(),
					failureResponse: agentsResponse(),
				}),
				mockQueryElementInstanceInspectionEndpoint({
					successResponse: HttpResponse.json(
						createQueryElementInstanceInspectionResponse([
							createElementInstanceInspection({elementInstanceKey: ELEMENT_INSTANCE_KEY}),
						]),
					),
				}),
				...handlers(),
			);
			const screen = await renderDetails({search: selected('service-task')});

			await expect.element(screen.getByText('Waiting for message: payment-received')).toBeVisible();
			await expect.element(screen.getByText('Element Instance Key')).toBeVisible();
			await expect.element(screen.getByRole('heading', {name: 'Agent Instance'})).not.toBeInTheDocument();
		});
	});

	describe('call activity', () => {
		const callActivity = instanceOf('call-activity', 'CALL_ACTIVITY');

		it('should show None when no process instance was called', async ({worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({
					successResponse: HttpResponse.json(createQueryProcessInstancesResponse({items: []})),
				}),
				...handlers({elementInstance: callActivity}),
			);
			const screen = await renderDetails({search: selected('call-activity')});

			await expect.element(screen.getByText('Called Process Instance')).toBeVisible();
			await expect.element(screen.getByText('None')).toBeVisible();
		});

		it('should link to the called process instance', async ({worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({
					successResponse: HttpResponse.json(
						createQueryProcessInstancesResponse({
							items: [createProcessInstance({processInstanceKey: '2251799813685777', processDefinitionName: 'Child'})],
						}),
					),
				}),
				...handlers({elementInstance: callActivity}),
			);
			const screen = await renderDetails({search: selected('call-activity')});

			await expect
				.element(screen.getByRole('link', {name: 'View Child instance 2251799813685777'}))
				.toHaveAttribute('href', '/operate/processes/2251799813685777');
		});

		it('should link to the filtered list when several process instances were called', async ({worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({
					successResponse: HttpResponse.json({
						...createQueryProcessInstancesResponse({items: [createProcessInstance()]}),
						page: {totalItems: 3, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				}),
				...handlers({elementInstance: callActivity}),
			);
			const screen = await renderDetails({search: selected('call-activity')});

			const link = screen.getByRole('link', {name: 'View all called process instances'});
			await expect.element(link).toHaveTextContent('View all (3)');
			await expect
				.element(link)
				.toHaveAttribute(
					'href',
					expect.stringMatching(new RegExp(`parentProcessInstanceKey=.*${PROCESS_INSTANCE_ID}`)),
				);
		});

		it('should fall back to the process definition id when the called process has no name', async ({worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({
					successResponse: HttpResponse.json(
						createQueryProcessInstancesResponse({
							items: [
								createProcessInstance({
									processInstanceKey: '2251799813685777',
									processDefinitionName: undefined,
									processDefinitionId: 'child-process',
								}),
							],
						}),
					),
				}),
				...handlers({elementInstance: callActivity}),
			);
			const screen = await renderDetails({search: selected('call-activity')});

			await expect
				.element(screen.getByRole('link', {name: 'View child-process instance 2251799813685777'}))
				.toHaveTextContent('child-process - 2251799813685777');
		});

		describe('tenant scoping of the filtered list', () => {
			const manyCalledHandlers = () => [
				mockQueryProcessInstancesEndpoint({
					successResponse: HttpResponse.json({
						...createQueryProcessInstancesResponse({items: [createProcessInstance()]}),
						page: {totalItems: 3, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				}),
				...handlers({elementInstance: callActivity}),
			];
			const tenantInstance = createProcessInstance({
				processInstanceKey: PROCESS_INSTANCE_ID,
				state: 'ACTIVE',
				tenantId: 'tenant-a',
			});

			it('should carry the tenant when multi-tenancy is enabled', async ({worker}) => {
				worker.use(...manyCalledHandlers());
				const screen = await renderDetails({
					search: selected('call-activity'),
					processInstance: tenantInstance,
					isMultiTenancyEnabled: true,
				});

				await expect
					.element(screen.getByRole('link', {name: 'View all called process instances'}))
					.toHaveAttribute('href', expect.stringContaining('tenantId=tenant-a'));
			});

			it('should not carry a tenant when multi-tenancy is disabled', async ({worker}) => {
				worker.use(...manyCalledHandlers());
				const screen = await renderDetails({search: selected('call-activity'), processInstance: tenantInstance});

				await expect
					.element(screen.getByRole('link', {name: 'View all called process instances'}))
					.toHaveAttribute('href', expect.not.stringContaining('tenantId'));
			});
		});
	});

	describe('business rule task', () => {
		const ruleTask = instanceOf('rule-task', 'BUSINESS_RULE_TASK');

		it('should link to the called decision instance', async ({worker}) => {
			worker.use(
				mockQueryDecisionInstancesEndpoint({
					successResponse: HttpResponse.json(
						createQueryDecisionInstancesResponse({
							items: [
								createDecisionInstance({decisionEvaluationInstanceKey: '999', decisionDefinitionName: 'Invoice'}),
							],
						}),
					),
				}),
				...handlers({elementInstance: ruleTask}),
			);
			const screen = await renderDetails({search: selected('rule-task')});

			await expect
				.element(screen.getByRole('link', {name: 'View Invoice instance 999'}))
				.toHaveAttribute('href', '/operate/decisions/999');
		});

		it('should show a dash when no decision was called', async ({worker}) => {
			worker.use(
				mockQueryDecisionInstancesEndpoint({
					successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: []})),
				}),
				...handlers({elementInstance: ruleTask}),
			);
			const screen = await renderDetails({search: selected('rule-task')});

			await expect.element(screen.getByRole('row', {name: 'Called Decision Instance -'})).toBeVisible();
		});
	});

	describe('user task', () => {
		it('should show the user task properties and a Tasklist link for a Camunda user task', async ({worker}) => {
			worker.use(
				mockQueryUserTasksEndpoint({
					successResponse: HttpResponse.json(
						createQueryUserTasksResponse({
							items: [
								createUserTask({
									userTaskKey: '2251799813685555',
									assignee: 'demo',
									candidateGroups: ['sales', 'ops'],
									priority: 75,
								}),
							],
						}),
					),
				}),
				...handlers({
					elementInstance: instanceOf('camunda-task', 'USER_TASK'),
				}),
			);
			const screen = await renderDetails({search: selected('camunda-task')});

			await expect.element(screen.getByText('demo')).toBeVisible();
			await expect.element(screen.getByText('sales, ops')).toBeVisible();
			await expect.element(screen.getByText('75')).toBeVisible();
			await expect
				.element(screen.getByRole('link', {name: 'Open Tasklist'}))
				.toHaveAttribute('href', expect.stringContaining('/tasklist/2251799813685555'));
		});

		it('should not show the Tasklist link when Tasklist is not active', async ({worker}) => {
			worker.use(
				mockQueryUserTasksEndpoint({
					successResponse: HttpResponse.json(
						createQueryUserTasksResponse({items: [createUserTask({assignee: 'demo'})]}),
					),
				}),
				...handlers({elementInstance: instanceOf('camunda-task', 'USER_TASK')}),
			);
			const screen = await renderDetails({search: selected('camunda-task'), activeComponents: ['operate']});

			await expect.element(screen.getByText('demo')).toBeVisible();
			await expect.element(screen.getByRole('link', {name: 'Open Tasklist'})).not.toBeInTheDocument();
		});

		it('should warn about deprecated job worker user tasks', async ({worker}) => {
			worker.use(
				...handlers({
					elementInstance: instanceOf('worker-task', 'USER_TASK'),
				}),
			);
			const screen = await renderDetails({search: selected('worker-task')});

			await expect.element(screen.getByText('User tasks with job worker implementation are deprecated.')).toBeVisible();
			await expect.element(screen.getByText('Assignee')).not.toBeInTheDocument();
		});
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
			...handlers({
				elementInstance: instanceOf('message-event', 'INTERMEDIATE_CATCH_EVENT'),
			}),
		);
		const screen = await renderDetails({search: selected('message-event')});

		await expect.element(screen.getByText('order-1')).toBeVisible();
		await expect.element(screen.getByText('CREATED')).toBeVisible();
	});

	it('should switch between process and element scope without remounting', async ({worker}) => {
		worker.use(
			mockQueryElementInstanceInspectionByFilterEndpoint({
				getResponse: (filter) =>
					HttpResponse.json(
						createQueryElementInstanceInspectionResponse([
							filter?.elementInstanceKey === PROCESS_INSTANCE_ID
								? createElementInstanceInspection({
										elementInstanceKey: PROCESS_INSTANCE_ID,
										details: {waitStateType: 'MESSAGE', messageName: 'process-message', correlationKey: null},
									})
								: createElementInstanceInspection({
										elementInstanceKey: ELEMENT_INSTANCE_KEY,
										details: {waitStateType: 'SIGNAL', signalName: 'element-signal'},
									}),
						]),
					),
			}),
			...handlers(),
		);
		const screen = await renderDetails();
		await expect.element(screen.getByText('Waiting for message: process-message')).toBeVisible();
		await expect.element(screen.getByText('Element Instance Key')).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'select element'}));
		await expect.element(screen.getByText('Waiting for signal: element-signal')).toBeVisible();
		await expect.element(screen.getByText('Element Instance Key')).toBeVisible();
		await expect.element(screen.getByText('Waiting for message: process-message')).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'select process'}));
		await expect.element(screen.getByText('Waiting for message: process-message')).toBeVisible();
		await expect.element(screen.getByText('Element Instance Key')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Waiting for signal: element-signal')).not.toBeInTheDocument();
	});

	it('should replace the details when another element instance is selected', async ({worker}) => {
		worker.use(
			mockQueryJobsEndpoint({
				successResponse: HttpResponse.json(createQueryJobsResponse([createJob({worker: 'first-worker'})])),
			}),
			...handlers(),
		);
		const screen = await renderDetails({search: selected('service-task')});
		await expect.element(screen.getByText('first-worker')).toBeVisible();

		worker.use(
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(createElementInstance({elementInstanceKey: '2251799813685999'})),
			}),
			mockQueryJobsEndpoint({
				successResponse: HttpResponse.json(
					createQueryJobsResponse([createJob({elementInstanceKey: '2251799813685999', worker: 'second-worker'})]),
				),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'select other instance'}));

		await expect.element(screen.getByText('second-worker')).toBeVisible();
		await expect.element(screen.getByText('first-worker')).not.toBeInTheDocument();
	});
});
