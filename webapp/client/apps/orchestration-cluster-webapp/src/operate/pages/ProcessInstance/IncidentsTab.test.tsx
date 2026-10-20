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
import {useParams, useRouterState} from '@tanstack/react-router';
import type {Incident, ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetElementInstanceEndpoint,
	mockGetIncidentEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockQueryElementInstanceIncidentsEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockResolveIncidentEndpoint,
	mockUpdateJobEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createIncident} from '#/shared-test-modules/api-mocks/incidents';
import {createElementInstance} from '#/shared-test-modules/api-mocks/element-instances';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {IncidentsTab} from './IncidentsTab';
import {processInstanceSearchSchema} from './processInstanceSearch';
import {notificationsStore} from '#/shared/notifications/notifications.store';

const PROCESS_INSTANCE_ID = '2251799813685280';
const ELEMENT_INSTANCE_KEY = '2251799813685281';

afterEach(async () => {
	await cleanup();
	notificationsStore.reset();
});

function incidentsResponse(items: Incident[]) {
	return HttpResponse.json(
		createPaginatedResponse({
			items,
			page: {totalItems: items.length, startCursor: null, endCursor: null, hasMoreTotalItems: false},
		}),
	);
}

const serverError = () => HttpResponse.json(createProblemDetails({status: 500}), {status: 500});

function enrichment() {
	return [
		mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
		mockQueryProcessInstancesEndpoint({successResponse: HttpResponse.json(createQueryProcessInstancesResponse())}),
		mockQueryDecisionInstancesEndpoint({successResponse: HttpResponse.json(createQueryDecisionInstancesResponse())}),
	];
}

function incidentRow(screen: Awaited<ReturnType<typeof renderIncidents>>, errorMessage: string) {
	return screen.getByRole('row').filter({hasText: errorMessage});
}

function renderIncidents(search = '', processInstance: Partial<ProcessInstance> = {}) {
	function Incidents() {
		const routeSearch = useRouterState({select: (state) => state.location.search});
		const {processInstanceId = PROCESS_INSTANCE_ID} = useParams({strict: false});
		return (
			<div style={{height: '400px', display: 'flex', flexDirection: 'column'}}>
				<ProcessInstancePageProvider
					processInstanceId={processInstanceId}
					processInstance={createProcessInstance({
						processInstanceKey: processInstanceId,
						hasIncident: true,
						...processInstance,
					})}
					search={processInstanceSearchSchema.parse(routeSearch)}
				>
					<IncidentsTab />
				</ProcessInstancePageProvider>
			</div>
		);
	}

	return renderWithRouter(Incidents, {
		path: '/operate/processes/$processInstanceId/incidents',
		initialEntry: `/operate/processes/${PROCESS_INSTANCE_ID}/incidents${search}`,
	});
}

describe('<IncidentsTab />', () => {
	it('should show the active process instance incidents, newest first', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockQueryProcessInstanceIncidentsEndpoint({
				schema: z
					.object({
						filter: z.object({state: z.literal('ACTIVE')}).strict(),
						sort: z.tuple([z.object({field: z.literal('creationTime'), order: z.literal('DESC')})]),
						page: z.object({from: z.literal(0), limit: z.literal(50)}).strict(),
					})
					.strict(),
				successResponse: incidentsResponse([createIncident()]),
				failureResponse: serverError(),
			}),
		);
		const screen = await renderIncidents();

		const row = incidentRow(screen, 'No more retries left.');
		await expect.element(row).toBeVisible();
		await expect.element(row.getByRole('cell', {name: 'Job: No retries left.'})).toBeVisible();
		await expect.element(row.getByRole('cell', {name: 'Review invoice'})).toBeVisible();
		await expect.element(screen.getByText('1 result')).toBeVisible();
	});

	it('should filter the incidents by error type and reset the filter', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockQueryProcessInstanceIncidentsEndpoint({
				schema: z.object({filter: z.object({errorType: z.object({$in: z.tuple([z.literal('IO_MAPPING_ERROR')])})})}),
				successResponse: incidentsResponse([
					createIncident({incidentKey: '2', errorType: 'IO_MAPPING_ERROR', errorMessage: 'Mapping failed'}),
				]),
				failureResponse: incidentsResponse([createIncident()]),
			}),
		);
		const screen = await renderIncidents();
		await expect.element(incidentRow(screen, 'No more retries left.')).toBeVisible();

		await userEvent.click(screen.getByRole('combobox', {name: 'Filter by Incident Type'}));
		await userEvent.click(screen.getByRole('option', {name: 'IO mapping error.'}));

		await expect.element(incidentRow(screen, 'Mapping failed')).toBeVisible();
		await expect.element(incidentRow(screen, 'No more retries left.')).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Reset Filters'}));

		await expect.element(incidentRow(screen, 'No more retries left.')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Reset Filters'})).toBeDisabled();
	});

	it('should query the incidents of the selected element instance', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(
					createElementInstance({elementId: 'task-1', elementInstanceKey: ELEMENT_INSTANCE_KEY, hasIncident: true}),
				),
			}),
			mockQueryProcessInstanceIncidentsEndpoint({successResponse: serverError()}),
			mockQueryElementInstanceIncidentsEndpoint({
				schema: z.object({filter: z.object({state: z.literal('ACTIVE'), elementId: z.literal('task-1')}).strict()}),
				successResponse: incidentsResponse([createIncident({errorMessage: 'Selected element failed'})]),
				failureResponse: serverError(),
			}),
		);
		const screen = await renderIncidents(`?elementId=task-1&elementInstanceKey=${ELEMENT_INSTANCE_KEY}`);

		await expect.element(incidentRow(screen, 'Selected element failed')).toBeVisible();
	});

	it('should link incidents to the decision and called instance that caused them', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockQueryProcessInstanceIncidentsEndpoint({
				successResponse: incidentsResponse([
					createIncident({
						incidentKey: '1',
						errorType: 'DECISION_EVALUATION_ERROR',
						elementId: 'decide',
						elementInstanceKey: '7001',
					}),
					createIncident({incidentKey: '2', processInstanceKey: '9001', elementId: 'child-task'}),
					createIncident({
						incidentKey: '3',
						processInstanceKey: '9001',
						errorType: 'CALLED_DECISION_ERROR',
						elementId: 'child-decide',
						elementInstanceKey: '7002',
					}),
				]),
			}),
			mockQueryDecisionInstancesEndpoint({
				schema: z.object({
					filter: z
						.object({elementInstanceKey: z.object({$in: z.tuple([z.literal('7001'), z.literal('7002')])})})
						.strict(),
				}),
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [
							createDecisionInstance({
								elementInstanceKey: '7001',
								decisionEvaluationInstanceKey: '123567',
								state: 'FAILED',
								evaluationDate: '2022-01-20T14:00:00.000Z',
							}),
							createDecisionInstance({
								elementInstanceKey: '7001',
								decisionEvaluationInstanceKey: '123560',
								state: 'FAILED',
								evaluationDate: '2022-01-20T13:00:00.000Z',
							}),
							createDecisionInstance({
								elementInstanceKey: '7001',
								decisionEvaluationInstanceKey: '123568',
								decisionDefinitionName: 'Required Decision',
								evaluationDate: '2022-01-20T14:00:00.000Z',
							}),
							createDecisionInstance({
								elementInstanceKey: '7002',
								processInstanceKey: '9001',
								decisionEvaluationInstanceKey: '123570',
								decisionDefinitionName: 'Child Decision',
								state: 'FAILED',
							}),
						],
					}),
				),
				failureResponse: serverError(),
			}),
			mockQueryProcessInstancesEndpoint({
				schema: z.object({
					filter: z.object({processInstanceKey: z.object({$in: z.tuple([z.literal('9001')])})}).strict(),
					page: z.object({limit: z.literal(1)}),
				}),
				failureResponse: serverError(),
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '9001', processDefinitionName: 'Child Process'})],
					}),
				),
			}),
		);
		const screen = await renderIncidents();

		await expect
			.element(screen.getByRole('link', {name: 'Invoice Classification - 123567'}))
			.toHaveAttribute('href', '/operate/decisions/123567');
		await expect
			.element(screen.getByRole('link', {name: 'child-task - Child Process - 9001'}))
			.toHaveAttribute('href', '/operate/processes/9001?elementId=child-task');
		await expect
			.element(screen.getByRole('link', {name: 'Child Decision - 123570'}))
			.toHaveAttribute('href', '/operate/decisions/123570');
		expect(screen.getByRole('button', {name: 'Retry Incident'}).elements()).toHaveLength(1);
	});

	it('should keep the tenant in the links to other instances', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockQueryProcessInstanceIncidentsEndpoint({
				successResponse: incidentsResponse([
					createIncident({
						incidentKey: '1',
						errorType: 'DECISION_EVALUATION_ERROR',
						elementId: 'decide',
						elementInstanceKey: '7001',
					}),
					createIncident({incidentKey: '2', processInstanceKey: '9001', elementId: 'child-task'}),
				]),
			}),
			mockQueryDecisionInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [
							createDecisionInstance({
								elementInstanceKey: '7001',
								decisionEvaluationInstanceKey: '123567',
								state: 'FAILED',
							}),
						],
					}),
				),
			}),
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '9001', processDefinitionName: 'Child Process'})],
					}),
				),
			}),
		);
		const screen = await renderIncidents('?tenantId=tenant-a');

		await expect
			.element(screen.getByRole('link', {name: 'Invoice Classification - 123567'}))
			.toHaveAttribute('href', '/operate/decisions/123567?tenantId=tenant-a');
		await expect
			.element(screen.getByRole('link', {name: 'child-task - Child Process - 9001'}))
			.toHaveAttribute('href', '/operate/processes/9001?elementId=child-task&tenantId=tenant-a');
	});

	it('should keep the tenant in the link to the called instance', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(
					createElementInstance({
						elementId: 'call',
						elementInstanceKey: ELEMENT_INSTANCE_KEY,
						type: 'CALL_ACTIVITY',
						hasIncident: true,
					}),
				),
			}),
			mockQueryElementInstanceIncidentsEndpoint({successResponse: incidentsResponse([])}),
			mockQueryProcessInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '9001', processDefinitionName: 'Child Process'})],
					}),
				),
			}),
		);
		const screen = await renderIncidents(
			`?elementId=call&elementInstanceKey=${ELEMENT_INSTANCE_KEY}&tenantId=tenant-a`,
		);

		await expect
			.element(screen.getByRole('link', {name: 'View in Child Process'}))
			.toHaveAttribute('href', '/operate/processes/9001?tenantId=tenant-a');
	});

	it('should point to the called instance when the selected call activity has no own incidents', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(
					createElementInstance({
						elementId: 'call',
						elementInstanceKey: ELEMENT_INSTANCE_KEY,
						type: 'CALL_ACTIVITY',
						hasIncident: true,
					}),
				),
			}),
			mockQueryElementInstanceIncidentsEndpoint({successResponse: incidentsResponse([])}),
			mockQueryProcessInstancesEndpoint({
				schema: z.object({
					filter: z.object({parentElementInstanceKey: z.literal(ELEMENT_INSTANCE_KEY), hasIncident: z.literal(true)}),
				}),
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '9001', processDefinitionName: 'Child Process'})],
					}),
				),
				failureResponse: serverError(),
			}),
		);
		const screen = await renderIncidents(`?elementId=call&elementInstanceKey=${ELEMENT_INSTANCE_KEY}`);

		await expect.element(screen.getByText('No incidents found on this element.')).toBeVisible();
		await expect
			.element(screen.getByRole('link', {name: 'View in Child Process'}))
			.toHaveAttribute('href', '/operate/processes/9001');
	});

	it('should point to the called instance once an incident appears below the selected call activity', async ({
		worker,
	}) => {
		const callActivity = {elementId: 'call', elementInstanceKey: ELEMENT_INSTANCE_KEY, type: 'CALL_ACTIVITY'} as const;
		worker.use(...enrichment());
		worker.use(
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(createElementInstance({...callActivity, hasIncident: false})),
				once: true,
			}),
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(createElementInstance({...callActivity, hasIncident: true})),
			}),
			mockQueryElementInstanceIncidentsEndpoint({successResponse: incidentsResponse([])}),
			mockQueryProcessInstancesEndpoint({
				schema: z.object({
					filter: z.object({parentElementInstanceKey: z.literal(ELEMENT_INSTANCE_KEY), hasIncident: z.literal(true)}),
				}),
				successResponse: HttpResponse.json(
					createQueryProcessInstancesResponse({
						items: [createProcessInstance({processInstanceKey: '9001', processDefinitionName: 'Child Process'})],
					}),
				),
				failureResponse: serverError(),
			}),
		);
		const screen = await renderIncidents(`?elementId=call&elementInstanceKey=${ELEMENT_INSTANCE_KEY}`);

		await expect.element(screen.getByText('No incidents found on this element.')).toBeVisible();
		await expect
			.element(screen.getByRole('link', {name: 'View in Child Process'}), {timeout: 10000})
			.toHaveAttribute('href', '/operate/processes/9001');
	});

	it('should show the job and full error message of an expanded incident without selecting it', async ({worker}) => {
		const errorMessage = 'Expression failed\nat line 3';
		worker.use(...enrichment());
		worker.use(
			mockQueryProcessInstanceIncidentsEndpoint({successResponse: incidentsResponse([createIncident({errorMessage})])}),
		);
		const screen = await renderIncidents();

		await userEvent.click(incidentRow(screen, 'Expression failed').getByRole('button', {name: 'Expand current row'}));

		await expect.element(screen.getByText('2251799813685291')).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'More'}));
		await expect.element(screen.getByRole('dialog', {name: 'Element "Review invoice" Error'})).toBeVisible();
		expect(screen.router.state.location.search).toEqual({});
	});

	it('should sort the incidents by the clicked column', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockQueryProcessInstanceIncidentsEndpoint({
				schema: z.object({sort: z.tuple([z.object({field: z.literal('errorType'), order: z.literal('DESC')})])}),
				successResponse: incidentsResponse([createIncident({errorMessage: 'Sorted by type'})]),
				failureResponse: incidentsResponse([createIncident()]),
			}),
		);
		const screen = await renderIncidents();
		await expect.element(incidentRow(screen, 'No more retries left.')).toBeVisible();

		await userEvent.click(screen.getByRole('columnheader', {name: 'Type'}).getByRole('button'));

		await expect.element(incidentRow(screen, 'Sorted by type')).toBeVisible();
		expect(screen.router.state.location.search).toEqual({sort: 'errorType+DESC'});
	});

	it('should point to the failed decision when the selected business rule task has no own incidents', async ({
		worker,
	}) => {
		worker.use(...enrichment());
		worker.use(
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(
					createElementInstance({
						elementId: 'decide',
						elementInstanceKey: ELEMENT_INSTANCE_KEY,
						type: 'BUSINESS_RULE_TASK',
						hasIncident: true,
					}),
				),
			}),
			mockQueryElementInstanceIncidentsEndpoint({successResponse: incidentsResponse([])}),
			mockQueryDecisionInstancesEndpoint({
				schema: z.object({filter: z.object({elementInstanceKey: z.literal(ELEMENT_INSTANCE_KEY)}).strict()}),
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [
							createDecisionInstance({decisionEvaluationInstanceKey: '123568', decisionDefinitionName: 'Required'}),
							createDecisionInstance({
								decisionEvaluationInstanceKey: '123567',
								decisionDefinitionName: 'Failing Decision',
								state: 'FAILED',
							}),
							createDecisionInstance({decisionEvaluationInstanceKey: '123569', decisionDefinitionName: 'Other'}),
						],
					}),
				),
				failureResponse: serverError(),
			}),
		);
		const screen = await renderIncidents(`?elementId=decide&elementInstanceKey=${ELEMENT_INSTANCE_KEY}`);

		await expect
			.element(screen.getByRole('link', {name: 'View in Failing Decision'}))
			.toHaveAttribute('href', '/operate/decisions/123567');
	});

	it('should reset the error type filter when another process instance opens', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockQueryProcessInstanceIncidentsEndpoint({
				schema: z.object({filter: z.object({state: z.literal('ACTIVE')}).strict()}),
				successResponse: incidentsResponse([createIncident()]),
				failureResponse: incidentsResponse([createIncident({errorType: 'IO_MAPPING_ERROR', errorMessage: 'Filtered'})]),
			}),
		);
		const screen = await renderIncidents();
		await userEvent.click(screen.getByRole('combobox', {name: 'Filter by Incident Type'}));
		await userEvent.click(screen.getByRole('option', {name: 'IO mapping error.'}));
		await expect.element(incidentRow(screen, 'Filtered')).toBeVisible();

		await screen.router.navigate({
			to: '/operate/processes/$processInstanceId/incidents',
			params: {processInstanceId: '2251799813685999'},
		});

		await expect.element(incidentRow(screen, 'No more retries left.')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Reset Filters'})).toBeDisabled();
	});

	it('should retry an incident by restoring its job retries and resolving it', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockQueryProcessInstanceIncidentsEndpoint({successResponse: incidentsResponse([createIncident()]), once: true}),
			mockQueryProcessInstanceIncidentsEndpoint({successResponse: incidentsResponse([])}),
			mockUpdateJobEndpoint({
				schema: z.object({changeset: z.object({retries: z.literal(1)}).strict()}).strict(),
				successResponse: new HttpResponse(null, {status: 204}),
				failureResponse: serverError(),
			}),
			mockResolveIncidentEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetIncidentEndpoint({successResponse: HttpResponse.json(createIncident({state: 'RESOLVED'}))}),
		);
		// The list also polls, so it empties even if the retry fails; the resolution request proves the job update passed.
		const resolutions: string[] = [];
		const onRequest = ({request}: {request: Request}) => {
			if (request.url.endsWith('/resolution')) {
				resolutions.push(request.url);
			}
		};
		worker.events.on('request:start', onRequest);
		try {
			const screen = await renderIncidents();

			await userEvent.click(screen.getByRole('button', {name: 'Retry Incident'}));

			await expect.element(screen.getByText('There are no incidents matching this filter set')).toBeVisible();
			expect(resolutions).toHaveLength(1);
			expect(screen.router.state.location.search).toEqual({});
		} finally {
			worker.events.removeListener('request:start', onRequest);
		}
	});

	it('should report a retry whose incident cannot be read back', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockQueryProcessInstanceIncidentsEndpoint({successResponse: incidentsResponse([createIncident({jobKey: null})])}),
			mockResolveIncidentEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetIncidentEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
			}),
		);
		const screen = await renderIncidents();

		await userEvent.click(screen.getByRole('button', {name: 'Retry Incident'}));

		await expect
			.poll(() => notificationsStore.notifications.map(({title}) => title))
			.toEqual(["You don't have permission to perform this operation"]);
		await expect.element(screen.getByRole('button', {name: 'Retry Incident'})).toBeEnabled();
	});

	it('should keep a pending retry disabled when its row is shown again', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockQueryProcessInstanceIncidentsEndpoint({
				schema: z.object({filter: z.object({state: z.literal('ACTIVE')}).strict()}),
				successResponse: incidentsResponse([createIncident({jobKey: null})]),
				failureResponse: incidentsResponse([]),
			}),
			mockResolveIncidentEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockGetIncidentEndpoint({successResponse: HttpResponse.json(createIncident({state: 'ACTIVE'}))}),
		);
		const screen = await renderIncidents();
		await userEvent.click(screen.getByRole('button', {name: 'Retry Incident'}));
		await expect.element(screen.getByRole('button', {name: 'Retry Incident'})).toBeDisabled();

		await userEvent.click(screen.getByRole('combobox', {name: 'Filter by Incident Type'}));
		await userEvent.click(screen.getByRole('option', {name: 'IO mapping error.'}));
		await expect.element(screen.getByRole('button', {name: 'Retry Incident'})).not.toBeInTheDocument();
		await userEvent.click(screen.getByRole('button', {name: 'Reset Filters'}));

		await expect.element(screen.getByRole('button', {name: 'Retry Incident'})).toBeDisabled();

		worker.use(mockGetIncidentEndpoint({successResponse: HttpResponse.json(createIncident({state: 'RESOLVED'}))}));
		await expect.element(screen.getByRole('button', {name: 'Retry Incident'})).toBeEnabled();
	});

	it('should disable retrying while the process instance is suspended', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(mockQueryProcessInstanceIncidentsEndpoint({successResponse: incidentsResponse([createIncident()])}));
		const screen = await renderIncidents('', {state: 'SUSPENDED'});

		await expect.element(screen.getByRole('button', {name: 'Retry Incident'})).toBeDisabled();
	});

	it('should select a multi-instance body incident as the body', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(
					createElementInstance({
						elementId: 'task-1',
						elementInstanceKey: ELEMENT_INSTANCE_KEY,
						type: 'MULTI_INSTANCE_BODY',
						hasIncident: true,
					}),
				),
			}),
			mockQueryProcessInstanceIncidentsEndpoint({successResponse: incidentsResponse([createIncident()])}),
			mockQueryElementInstanceIncidentsEndpoint({successResponse: incidentsResponse([createIncident()])}),
		);
		const screen = await renderIncidents();

		await userEvent.click(
			incidentRow(screen, 'No more retries left.').getByRole('cell', {name: 'Job: No retries left.'}),
		);

		await expect
			.poll(() => screen.router.state.location.search)
			.toEqual({elementId: 'task-1', elementInstanceKey: ELEMENT_INSTANCE_KEY, isMultiInstanceBody: true});
		await expect.element(incidentRow(screen, 'No more retries left.')).toHaveAttribute('aria-selected', 'true');
	});

	it('should mark a capped incident count as a lower bound', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockQueryProcessInstanceIncidentsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [createIncident()],
						page: {totalItems: 10000, startCursor: null, endCursor: null, hasMoreTotalItems: true},
					}),
				),
			}),
		);
		const screen = await renderIncidents();

		await expect.element(screen.getByText('10000+ results')).toBeVisible();
	});

	it('should show an error message when the incidents cannot be fetched', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(mockQueryProcessInstanceIncidentsEndpoint({successResponse: serverError()}));
		const screen = await renderIncidents();

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
	});

	it('should select the incident element on row click and clear it on a second click', async ({worker}) => {
		worker.use(...enrichment());
		worker.use(
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(
					createElementInstance({elementId: 'task-1', elementInstanceKey: ELEMENT_INSTANCE_KEY, hasIncident: true}),
				),
			}),
			mockQueryProcessInstanceIncidentsEndpoint({successResponse: incidentsResponse([createIncident()])}),
			mockQueryElementInstanceIncidentsEndpoint({successResponse: incidentsResponse([createIncident()])}),
		);
		const screen = await renderIncidents();

		await userEvent.click(incidentRow(screen, 'No more retries left.'));

		await expect
			.poll(() => screen.router.state.location.search)
			.toEqual({elementId: 'task-1', elementInstanceKey: ELEMENT_INSTANCE_KEY});
		await expect.element(incidentRow(screen, 'No more retries left.')).toHaveAttribute('aria-selected', 'true');

		await userEvent.click(incidentRow(screen, 'No more retries left.'));

		await expect.poll(() => screen.router.state.location.search).toEqual({});
	});
});
