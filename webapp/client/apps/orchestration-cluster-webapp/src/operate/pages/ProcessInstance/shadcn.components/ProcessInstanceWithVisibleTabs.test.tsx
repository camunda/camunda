/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {afterEach, beforeEach, describe, expect} from 'vitest';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryElementInstanceIncidentsEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {createIncident} from '#/shared-test-modules/api-mocks/incidents';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {storeSessionState} from '#/shared/browser-storage/session-storage';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {ProcessInstancePageProvider} from '../ProcessInstancePageProvider';
import {processInstanceSearchSchema} from '../processInstanceSearch';
import {ProcessInstanceWithVisibleTabs} from './ProcessInstanceWithVisibleTabs';

const PROCESS_INSTANCE_ID = '2251799813685280';

beforeEach(() => {
	storeSessionState('clientConfig', createSystemConfiguration());
});

afterEach(async () => {
	await cleanup();
});

type ShellProps = {
	activeTab?: React.ComponentProps<typeof ProcessInstanceWithVisibleTabs>['activeTab'];
	hasIncident?: boolean;
	search?: Record<string, string>;
};

const ProcessInstanceShell: React.FC<ShellProps> = ({activeTab = 'variables', hasIncident = false, search = {}}) => (
	<div style={{height: '400px'}} className="flex flex-col">
		<ProcessInstancePageProvider
			processInstanceId={PROCESS_INSTANCE_ID}
			processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident})}
			search={processInstanceSearchSchema.parse(search)}
		>
			<ProcessInstanceWithVisibleTabs activeTab={activeTab} header={<p>Header</p>}>
				<p>Tab content</p>
			</ProcessInstanceWithVisibleTabs>
		</ProcessInstancePageProvider>
	</div>
);

const noWaitStates = mockGetProcessInstanceWaitStateStatisticsEndpoint({
	successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
});

const renderShell = (props: ShellProps, tab = '') =>
	renderWithRouter(() => <ProcessInstanceShell {...props} />, {
		path: '/operate-preview/processes/$processInstanceId',
		initialEntry: `/operate-preview/processes/${PROCESS_INSTANCE_ID}${tab}`,
	});

const ProcessInstanceWithResolvableIncidents: React.FC = () => {
	const [hasIncident, setHasIncident] = useState(true);

	return (
		<div style={{height: '400px'}} className="flex flex-col">
			<button type="button" onClick={() => setHasIncident(false)}>
				Resolve incidents
			</button>
			<ProcessInstancePageProvider
				processInstanceId={PROCESS_INSTANCE_ID}
				processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID, hasIncident})}
				search={processInstanceSearchSchema.parse({})}
			>
				<ProcessInstanceWithVisibleTabs activeTab="variables" header={<p>Header</p>} />
			</ProcessInstancePageProvider>
		</div>
	);
};

describe('<ProcessInstanceWithVisibleTabs /> (design system)', () => {
	it('should hide the incidents tab once the process instance has no incidents anymore', async ({worker}) => {
		worker.use(
			noWaitStates,
			mockQueryProcessInstanceIncidentsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [createIncident()],
						page: {totalItems: 3, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);
		const screen = await renderWithRouter(ProcessInstanceWithResolvableIncidents, {
			path: '/operate-preview/processes/$processInstanceId',
			initialEntry: `/operate-preview/processes/${PROCESS_INSTANCE_ID}`,
		});
		await expect.element(screen.getByRole('tab', {name: 'Incidents 3'})).toBeVisible();

		await screen.getByRole('button', {name: 'Resolve incidents'}).click();

		await expect.element(screen.getByRole('tab', {name: /^Incidents/})).not.toBeInTheDocument();
	});

	it('should hide the incidents and details tabs when there is no incident, no selection, and no process-level wait state', async ({
		worker,
	}) => {
		worker.use(noWaitStates);
		const screen = await renderShell({});

		await expect.element(screen.getByRole('tab', {name: 'Variables'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Listeners'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Incidents'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('tab', {name: 'Details'})).not.toBeInTheDocument();
	});

	it('should show the incidents tab when the process instance has incidents', async ({worker}) => {
		worker.use(
			noWaitStates,
			mockQueryProcessInstanceIncidentsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [createIncident()],
						page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);
		const screen = await renderShell({hasIncident: true});

		await expect.element(screen.getByRole('tab', {name: 'Incidents 2'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Details'})).not.toBeInTheDocument();
	});

	it('should hide the incidents tab when the selected element has no incidents', async ({worker}) => {
		worker.use(
			noWaitStates,
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryElementInstancesResponse([createElementInstance({elementId: 'task'})]),
				),
			}),
			mockQueryElementInstanceIncidentsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [],
						page: {totalItems: 0, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
			mockQueryProcessInstanceIncidentsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [],
						page: {totalItems: 0, startCursor: null, endCursor: null, hasMoreTotalItems: false},
					}),
				),
			}),
		);
		const screen = await renderShell({hasIncident: true, search: {elementId: 'task'}});

		await expect.element(screen.getByRole('tab', {name: 'Details'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Incidents'})).not.toBeInTheDocument();
	});

	it('should redirect a hidden incidents tab to variables without rendering its content', async ({worker}) => {
		worker.use(noWaitStates);
		const screen = await renderShell({activeTab: 'incidents'});

		await expect
			.poll(() => screen.router.state.location.pathname)
			.toBe(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/variables`);
		await expect.element(screen.getByText('Tab content')).not.toBeInTheDocument();
	});

	it('should redirect the history tab to variables when the wait-state statistics request fails', async ({worker}) => {
		worker.use(
			mockGetProcessInstanceWaitStateStatisticsEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderShell({activeTab: 'history'});

		await expect
			.poll(() => screen.router.state.location.pathname)
			.toBe(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/variables`);
	});
});
