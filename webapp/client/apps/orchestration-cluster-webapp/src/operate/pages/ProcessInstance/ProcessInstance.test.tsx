/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent, page} from 'vitest/browser';
import {HttpResponse, http, delay} from 'msw';
import {Button} from '@carbon/react';
import styled from 'styled-components';
import {cleanup} from 'vitest-browser-react';
import {useParams, useSearch} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockCurrentUserEndpoint,
	mockGetProcessInstanceEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ProcessInstance} from './ProcessInstance';
import {processInstanceSearchSchema} from './processInstanceSearch';
import {useProcessInstancePage} from './useProcessInstancePage';
import {processInstanceQuery, waitStateStatisticsQuery} from './processInstance.queries';

const ID = '2251799813685280';
const PageContainer = styled.div`
	height: 900px;
`;
const XML =
	'<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test"><process id="my-process"><callActivity id="call" /></process></definitions>';

function ChildPanel() {
	const {selection, activeTab, selectElement, selectElementInstance, clearSelection, setActiveTab} =
		useProcessInstancePage();
	return (
		<>
			<output aria-label="Selection">{JSON.stringify(selection)}</output>
			<output aria-label="Active tab">{activeTab}</output>
			<Button onClick={() => selectElement({elementId: 'task'})}>Select element</Button>
			<Button onClick={() => selectElement({elementId: 'call'})}>Select call</Button>
			<Button onClick={() => setActiveTab('history')}>History tab</Button>
			<Button
				onClick={() =>
					selectElementInstance({
						elementId: 'task',
						elementInstanceKey: '999',
						isPlaceholder: true,
						anchorElementId: 'anchor',
					})
				}
			>
				Select instance
			</Button>
			<Button onClick={clearSelection}>Clear selection</Button>
			<Button onClick={() => setActiveTab('details')}>Details tab</Button>
		</>
	);
}

function TestPage() {
	const {processInstanceId = ID} = useParams({strict: false});
	const search = processInstanceSearchSchema.parse(useSearch({strict: false}));
	return (
		<PageContainer>
			<ProcessInstance processInstanceId={processInstanceId} search={search} bottomPanel={<ChildPanel />} />
		</PageContainer>
	);
}

function renderPage(search = '') {
	return renderWithRouter(TestPage, {
		path: '/operate/processes/$processInstanceId',
		initialEntry: `/operate/processes/${ID}${search}`,
	});
}

describe('Process Instance shell', () => {
	beforeEach(async () => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		await page.viewport(1600, 1000);
	});
	afterEach(async () => {
		await cleanup();
		sessionStorage.clear();
		localStorage.clear();
		notificationsStore.reset();
	});

	function supportingMocks(xmlDelay = 0) {
		return [
			mockGetProcessInstanceWaitStateStatisticsEndpoint({successResponse: HttpResponse.json({items: []})}),
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({tenants: [{tenantId: 'tenant-a', name: 'Tenant A', description: null}]}),
				),
			}),
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(XML), delay: xmlDelay}),
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			}),
			mockGetProcessInstanceCallHierarchyEndpoint({successResponse: HttpResponse.json([])}),
		];
	}

	it('should render metadata, tenant-specific version and called-instance links without write actions', async ({
		worker,
	}) => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(createSystemConfiguration({deployment: {isMultiTenancyEnabled: true, maxRequestSize: 1000000}})),
		);
		worker.use(
			...supportingMocks(1500),
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(
					createProcessInstance({
						tenantId: 'tenant-a',
						processDefinitionVersionTag: 'production',
						businessId: 'order-42',
						endDate: '2026-01-16T10:00:00Z',
						state: 'COMPLETED',
					}),
				),
			}),
		);
		const previousTitle = document.title;
		const screen = await renderPage();
		await page.viewport(1600, 1000);
		await expect.element(screen.getByRole('columnheader', {name: 'Called Instances'})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: 'Version Tag'})).not.toBeInTheDocument();
		await expect.element(screen.getByText('My Process', {exact: true})).toBeVisible();
		for (const value of [ID, 'production', 'order-42', 'Tenant A']) {
			await expect.element(screen.getByRole('cell', {name: value, exact: true})).toBeVisible();
		}
		await expect.element(screen.getByRole('columnheader', {name: 'End Date'})).toBeVisible();
		const version = screen.getByRole('link', {name: /View process.*version 1/});
		const versionUrl = new URL(version.element().getAttribute('href')!, location.origin);
		expect(versionUrl.searchParams.get('tenantId')).toBe('tenant-a');
		expect(versionUrl.searchParams.get('process')).toBe('my-process');
		expect(versionUrl.searchParams.get('version')).toBe('1');
		const calledUrl = new URL(
			screen.getByRole('link', {name: 'View all called instances'}).element().getAttribute('href')!,
			location.origin,
		);
		expect(JSON.parse(calledUrl.searchParams.get('parentProcessInstanceKey')!)).toBe(ID);
		await expect.element(screen.getByRole('button', {name: 'Cancel Instance'})).not.toBeInTheDocument();
		expect(document.title).toBe(`Operate: Process Instance ${ID} of My Process`);
		localStorage.setItem('operate.panelStates', JSON.stringify({isProcessesFiltersCollapsed: true, other: true}));
		await userEvent.click(screen.getByRole('link', {name: 'View all called instances'}));
		expect(JSON.parse(localStorage.getItem('operate.panelStates')!)).toEqual({
			isProcessesFiltersCollapsed: false,
			other: true,
		});
		await expect.poll(() => document.title).toBe(previousTitle);
	});

	it('should hide absent metadata, use the definition ID fallback and reduce the header on small screens', async ({
		worker,
	}) => {
		await page.viewport(1024, 768);
		worker.use(
			...supportingMocks(),
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json({...createProcessInstance(), processDefinitionName: null}),
			}),
		);
		const screen = await renderPage('?elementId=task');
		await expect.element(screen.getByText('my-process', {exact: true})).toBeVisible();
		await expect.element(screen.getByLabelText('Active tab')).toHaveTextContent('details');
		for (const name of ['Tenant', 'Version Tag', 'Business ID', 'Start Date', 'End Date', 'Called Instances']) {
			await expect.element(screen.getByRole('columnheader', {name, exact: true})).not.toBeInTheDocument();
		}
	});

	it('should show suspended state ahead of incidents and reuse draining metadata', async ({worker}) => {
		worker.use(...supportingMocks());
		worker.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProcessInstance({state: 'SUSPENDED', hasIncident: true})),
			}),
			mockQueryProcessInstanceIncidentsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [createProcessDefinition({processDefinitionKey: '2251799813685279', state: 'DRAINING'})],
					}),
				),
			}),
		);
		const screen = await renderPage();
		await expect.element(screen.getByText('2 Incidents')).toBeVisible();
		await expect.element(screen.getByTestId('SUSPENDED-icon')).toBeVisible();
		await expect.element(screen.getByText('Draining', {exact: true})).toBeVisible();
		await expect.element(screen.getByLabelText('Active tab')).toHaveTextContent('incidents');
	});

	it('should show a loading skeleton and recover from a failed request', async ({worker}) => {
		worker.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
				delay: 1500,
			}),
		);
		const screen = await renderPage();
		await expect.element(screen.getByRole('columnheader', {name: 'Process Instance Key'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Retry', exact: true})).toBeVisible();
		worker.use(
			...supportingMocks(),
			mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(createProcessInstance())}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Retry', exact: true}));
		await expect.element(screen.getByText('My Process', {exact: true})).toBeVisible();
	});

	it.for([403, 500])(
		'should handle a cached %i response without exposing forbidden or discarding usable data',
		async (status, {worker}) => {
			worker.use(
				...supportingMocks(),
				mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(createProcessInstance())}),
			);
			const screen = await renderPage();
			await expect.element(screen.getByText('My Process', {exact: true})).toBeVisible();
			worker.use(
				mockGetProcessInstanceEndpoint({
					successResponse: HttpResponse.json(createProblemDetails({status}), {status}),
				}),
			);
			await screen.queryClient.invalidateQueries({queryKey: processInstanceQuery(ID).queryKey});
			if (status === 403) {
				await expect
					.element(screen.getByText('403 - You do not have permission to view this information'))
					.toBeVisible();
				await expect.element(screen.getByText('My Process', {exact: true})).not.toBeInTheDocument();
			} else {
				await expect.element(screen.getByText('My Process', {exact: true})).toBeVisible();
				await expect.element(screen.getByRole('button', {name: 'Retry', exact: true})).not.toBeInTheDocument();
			}
		},
	);

	it('should replace a missing instance with the running Processes list and notify once', async ({worker}) => {
		worker.use(
			mockGetProcessInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 404}), {status: 404}),
			}),
		);
		const screen = await renderPage();
		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/processes');
		expect(screen.router.state.location.search).toMatchObject({
			active: true,
			incidents: true,
			completed: false,
			canceled: false,
		});
		expect(notificationsStore.notifications.map(({title}) => title)).toEqual([`Instance ${ID} could not be found`]);
	});

	it('should collapse deep call hierarchies and navigate to hidden ancestors', async ({worker}) => {
		worker.use(...supportingMocks());
		worker.use(
			mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(createProcessInstance())}),
			mockGetProcessInstanceCallHierarchyEndpoint({
				successResponse: HttpResponse.json([
					...Array.from({length: 6}, (_, index) =>
						createCallHierarchy({processInstanceKey: String(index + 1), processDefinitionName: `Parent ${index + 1}`}),
					),
					createCallHierarchy({processInstanceKey: ID}),
				]),
			}),
		);
		const screen = await renderPage();
		await expect.element(screen.getByRole('link', {name: 'Parent 1'})).toHaveAttribute('href', '/operate/processes/1');
		await expect.element(screen.getByRole('link', {name: 'Parent 6'})).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'More'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Parent 3'}));
		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate/processes/3');
	});

	it('should keep selection and active tab in the URL with replacement selection and back navigation', async ({
		worker,
	}) => {
		worker.use(
			...supportingMocks(),
			mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(createProcessInstance())}),
		);
		const screen = await renderPage('?tab=history&elementId=old&elementInstanceKey=123&isMultiInstanceBody=true');
		await expect.element(screen.getByLabelText('Active tab')).toHaveTextContent('history');
		await userEvent.click(screen.getByRole('button', {name: 'Select element', exact: true}));
		expect(screen.router.state.location.search).toEqual({tab: 'history', elementId: 'task'});
		await userEvent.click(screen.getByRole('button', {name: 'Select instance', exact: true}));
		expect(screen.router.state.location.search).toMatchObject({
			elementInstanceKey: '999',
			isPlaceholder: true,
			anchorElementId: 'anchor',
		});
		await userEvent.click(screen.getByRole('button', {name: 'Clear selection'}));
		expect(screen.router.state.location.search).toEqual({tab: 'history'});
		await userEvent.click(screen.getByRole('button', {name: 'Details tab'}));
		await expect.element(screen.getByLabelText('Active tab')).toHaveTextContent('variables');
		screen.router.history.back();
		await expect.element(screen.getByLabelText('Active tab')).toHaveTextContent('history');
	});

	it.for([
		[true, 'ACTIVE', false, 1, 'my-process', 'details', 'Waiting'],
		[true, 'ACTIVE', false, 2, 'my-process', 'details', '2 waiting'],
		[true, 'ACTIVE', false, 0, 'my-process', 'details', null],
		[true, 'ACTIVE', false, 2, 'task', 'variables', null],
		[true, 'ACTIVE', false, 0, 'ERROR', 'variables', null],
		[false, 'ACTIVE', false, 2, 'my-process', 'variables', null],
		[true, 'COMPLETED', false, 2, 'my-process', 'variables', null],
		[true, 'SUSPENDED', false, 2, 'my-process', 'variables', null],
		[true, 'SUSPENDED', true, 2, 'my-process', 'incidents', '2 waiting'],
	] as const)(
		'should preserve wait-state gating and tab precedence (%j)',
		async ([isWaitStatesEnabled, state, hasIncident, waitingCount, elementId, tab, label], {worker}) => {
			sessionStorage.setItem(
				'clientConfig',
				JSON.stringify(createSystemConfiguration({deployment: {isWaitStatesEnabled}})),
			);
			let requests = 0;
			worker.use(
				...supportingMocks(),
				mockGetProcessInstanceEndpoint({
					successResponse: HttpResponse.json(createProcessInstance({state, hasIncident})),
				}),
				mockQueryProcessInstanceIncidentsEndpoint({successResponse: HttpResponse.json(createPaginatedResponse())}),
			);
			worker.use(
				http.get('/v2/process-instances/:key/statistics/wait-states', () => {
					requests++;
					return HttpResponse.json({items: [{elementId, waitingCount}]}, {status: elementId === 'ERROR' ? 500 : 200});
				}),
			);
			const screen = await renderPage();
			await expect.element(screen.getByLabelText('Active tab')).toHaveTextContent(tab);
			await expect.element(screen.getByText('My Process', {exact: true})).toBeVisible();
			if (label) {
				await expect.element(screen.getByText(label, {exact: true})).toBeVisible();
			} else {
				await expect.element(screen.getByText(/^(Waiting|\d+ waiting)$/)).not.toBeInTheDocument();
			}
			expect(requests).toBe(isWaitStatesEnabled && (state === 'ACTIVE' || hasIncident) ? 1 : 0);
		},
	);

	it.for([true, false])(
		'should defer tabs, poll and clear cached waits on completion or flag disablement (%s)',
		{timeout: 10000},
		async (isWaitStatesEnabled, {worker}) => {
			let requests = 0;
			worker.use(
				...supportingMocks(),
				mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(createProcessInstance())}),
			);
			worker.use(
				http.get('/v2/process-instances/:key/statistics/wait-states', async () => {
					if (requests === 0) {
						await delay(1500);
					}
					return HttpResponse.json({items: [{elementId: 'my-process', waitingCount: ++requests}]});
				}),
			);
			const search = isWaitStatesEnabled ? '' : '?tab=details';
			const screen = await renderPage(search);
			await expect.element(screen.getByText('My Process', {exact: true})).toBeVisible();
			expect(screen.getByLabelText('Active tab').element().textContent).toBe(search ? 'details' : '');
			await expect.element(screen.getByText('Waiting', {exact: true})).toBeVisible();
			await expect.poll(() => requests, {timeout: 7000}).toBe(2);
			await expect.element(screen.getByText('2 waiting', {exact: true})).toBeVisible();
			const instance = createProcessInstance({
				state: isWaitStatesEnabled ? 'COMPLETED' : 'ACTIVE',
				businessId: 'updated',
			});
			const config = createSystemConfiguration({deployment: {isWaitStatesEnabled}});
			sessionStorage.setItem('clientConfig', JSON.stringify(config));
			screen.queryClient.setQueryData(processInstanceQuery(ID).queryKey, instance);
			await expect.element(screen.getByText('2 waiting', {exact: true})).not.toBeInTheDocument();
			await expect.element(screen.getByLabelText('Active tab')).toHaveTextContent('variables');
			expect(waitStateStatisticsQuery(instance).refetchInterval).toBe(false);
		},
	);

	it('should validate shareable state without losing numeric identity precision', () => {
		expect(
			processInstanceSearchSchema.parse({elementId: 123, elementInstanceKey: '9007199254740993', tab: 'invalid'}),
		).toEqual({
			elementId: '123',
			elementInstanceKey: '9007199254740993',
			tab: undefined,
		});
	});

	it.for([false, true])(
		'should select the first tab atomically and defer call-activity switching until XML is ready (incidents: %s)',
		async (hasIncident, {worker}) => {
			worker.use(
				...supportingMocks(1500),
				mockGetProcessInstanceEndpoint({successResponse: HttpResponse.json(createProcessInstance({hasIncident}))}),
				mockQueryProcessInstanceIncidentsEndpoint({successResponse: HttpResponse.json(createPaginatedResponse())}),
			);
			const screen = await renderPage('?tab=variables');
			await userEvent.click(screen.getByRole('button', {name: 'Select element', exact: true}));
			const tab = hasIncident ? 'incidents' : 'details';
			expect(screen.router.state.location.search).toEqual({tab, elementId: 'task'});
			await userEvent.click(screen.getByRole('button', {name: 'History tab'}));
			await userEvent.click(screen.getByRole('button', {name: 'Select call'}));
			await expect.element(screen.getByText('My Process', {exact: true})).toBeVisible();
			await expect.poll(() => screen.router.state.location.search).toEqual({tab, elementId: 'call'});
		},
	);
});
