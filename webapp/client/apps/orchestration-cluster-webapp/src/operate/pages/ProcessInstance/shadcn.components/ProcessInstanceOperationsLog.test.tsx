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
import {useRouterState} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetElementInstanceEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryAuditLogsEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockQueryElementInstanceInspectionEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryJobsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createQueryJobsResponse} from '#/shared-test-modules/api-mocks/jobs';
import {createQueryElementInstanceInspectionResponse} from '#/shared-test-modules/api-mocks/element-instance-inspection';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createAuditLog, createQueryAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {ProcessInstancePageProvider} from '../ProcessInstancePageProvider';
import {InstanceHistory} from './InstanceHistory';
import {ProcessInstanceDetails} from './ProcessInstanceDetails';
import {ProcessInstanceOperationsLog} from './ProcessInstanceOperationsLog';
import {processInstanceSearchSchema} from '../processInstanceSearch';
import {processInstanceOperationsLogSearchSchema} from '../processInstanceOperationsLog.schema';

const PROCESS_INSTANCE_ID = '2251799813685280';

afterEach(async () => {
	await cleanup();
	sessionStorage.clear();
});

function auditLogsResponse(...items: ReturnType<typeof createAuditLog>[]) {
	return HttpResponse.json(createQueryAuditLogsResponse({items}));
}

function renderOperationsLog(search = '') {
	function OperationsLog() {
		const routeSearch = useRouterState({select: (state) => state.location.search});
		return (
			<div style={{height: '400px', display: 'flex', flexDirection: 'column'}}>
				<ProcessInstancePageProvider
					processInstanceId={PROCESS_INSTANCE_ID}
					processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})}
					search={processInstanceSearchSchema.parse(routeSearch)}
				>
					<ProcessInstanceOperationsLog search={processInstanceOperationsLogSearchSchema.parse(routeSearch)} />
				</ProcessInstancePageProvider>
			</div>
		);
	}

	return renderWithRouter(OperationsLog, {
		path: '/operate-preview/processes/$processInstanceId/operations-log',
		initialEntry: `/operate-preview/processes/${PROCESS_INSTANCE_ID}/operations-log${search}`,
	});
}

function renderOperationsLogWithHistory(search: string) {
	function OperationsLogWithHistory() {
		const routeSearch = useRouterState({select: (state) => state.location.search});
		return (
			<ProcessInstancePageProvider
				processInstanceId={PROCESS_INSTANCE_ID}
				processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})}
				search={processInstanceSearchSchema.parse(routeSearch)}
			>
				<div style={{height: '300px', display: 'flex', flexDirection: 'column'}}>
					<InstanceHistory />
				</div>
				<ProcessInstanceDetails />
				<div style={{height: '400px', display: 'flex', flexDirection: 'column'}}>
					<ProcessInstanceOperationsLog search={processInstanceOperationsLogSearchSchema.parse(routeSearch)} />
				</div>
			</ProcessInstancePageProvider>
		);
	}

	return renderWithRouter(OperationsLogWithHistory, {
		path: '/operate-preview/processes/$processInstanceId/operations-log',
		initialEntry: `/operate-preview/processes/${PROCESS_INSTANCE_ID}/operations-log${search}`,
	});
}

describe('<ProcessInstanceOperationsLog /> (design system)', () => {
	it('should list the non-admin operations of the process instance, newest first', async ({worker}) => {
		worker.use(
			mockQueryAuditLogsEndpoint({
				schema: z.object({
					filter: z.object({
						category: z.object({$neq: z.literal('ADMIN')}),
						processInstanceKey: z.literal(PROCESS_INSTANCE_ID),
					}),
					sort: z.tuple([z.object({field: z.literal('timestamp'), order: z.literal('DESC')})]),
				}),
				successResponse: auditLogsResponse(
					createAuditLog({auditLogKey: '1', operationType: 'CANCEL', entityType: 'PROCESS_INSTANCE', actorId: 'jane'}),
				),
				failureResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderOperationsLog();

		await expect.element(screen.getByRole('cell', {name: 'Cancel'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Process instance'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'User jane'})).toBeVisible();
	});

	it('should keep the tenant when opening a process or decision from a tenant-scoped operations log', async ({
		worker,
	}) => {
		const elementInstance = createElementInstance({elementInstanceKey: '2251799813685299'});
		worker.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
			mockQueryAuditLogsEndpoint({
				schema: z.object({filter: z.object({processInstanceKey: z.literal(PROCESS_INSTANCE_ID)})}),
				successResponse: auditLogsResponse(
					createAuditLog({
						auditLogKey: '1',
						entityType: 'PROCESS_INSTANCE',
						entityKey: '9001',
						operationType: 'CANCEL',
					}),
					createAuditLog({auditLogKey: '2', entityType: 'DECISION', entityKey: '9002', operationType: 'EVALUATE'}),
				),
				failureResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderOperationsLog(
			`?tenantId=tenant-a&elementId=user-task&elementInstanceKey=${elementInstance.elementInstanceKey}`,
		);

		await expect
			.element(screen.getByRole('link', {name: 'View process instance 9001'}))
			.toHaveAttribute('href', '/operate-preview/processes/9001?tenantId=tenant-a');
		await expect
			.element(screen.getByRole('link', {name: 'View decision instance 9002'}))
			.toHaveAttribute('href', '/operate/decisions/9002?tenantId=tenant-a');
	});

	it('should scope the operations to the selected element instance', async ({worker}) => {
		const elementInstance = createElementInstance({elementInstanceKey: '2251799813685299'});
		worker.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
			mockQueryAuditLogsEndpoint({
				schema: z.object({filter: z.object({elementInstanceKey: z.literal(elementInstance.elementInstanceKey)})}),
				successResponse: auditLogsResponse(createAuditLog({operationType: 'MIGRATE'})),
				failureResponse: auditLogsResponse(createAuditLog({operationType: 'DELETE'})),
			}),
		);
		const screen = await renderOperationsLog(
			`?elementId=user-task&elementInstanceKey=${elementInstance.elementInstanceKey}`,
		);

		await expect.element(screen.getByRole('cell', {name: 'Migrate'})).toBeVisible();
	});

	it('should not show the previous element instance operations while the new scope loads', async ({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		const selectedElementInstance = createElementInstance({
			elementId: 'user-task',
			elementName: 'Review task',
			elementInstanceKey: '2251799813685299',
		});
		const otherElementInstance = createElementInstance({
			elementId: 'other-task',
			elementName: 'Approve task',
			elementInstanceKey: '2251799813685300',
		});
		worker.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text('')}),
			mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createPaginatedResponse())}),
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(
					createQueryElementInstancesResponse([selectedElementInstance, otherElementInstance]),
				),
			}),
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(selectedElementInstance)}),
			mockQueryJobsEndpoint({successResponse: HttpResponse.json(createQueryJobsResponse())}),
			mockQueryElementInstanceInspectionEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstanceInspectionResponse()),
			}),
			mockQueryAuditLogsEndpoint({successResponse: auditLogsResponse(createAuditLog({operationType: 'CANCEL'}))}),
		);
		const screen = await renderOperationsLogWithHistory('?elementId=user-task&elementInstanceKey=2251799813685299');
		await expect.element(screen.getByRole('cell', {name: 'Cancel'})).toBeVisible();

		worker.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(otherElementInstance)}),
			mockQueryAuditLogsEndpoint({
				successResponse: auditLogsResponse(createAuditLog({operationType: 'MIGRATE'})),
				delay: 'infinite',
			}),
		);
		await userEvent.click(screen.getByRole('treeitem', {name: /Approve task/}));

		await expect.element(screen.getByRole('row', {name: 'Element Instance Key 2251799813685300'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Cancel'})).not.toBeInTheDocument();
	});

	it('should apply the operation type, entity type and sort from the URL', async ({worker}) => {
		worker.use(
			mockQueryAuditLogsEndpoint({
				schema: z.object({
					filter: z.object({
						operationType: z.object({$in: z.tuple([z.literal('ASSIGN'), z.literal('CANCEL')])}),
						entityType: z.object({$in: z.tuple([z.literal('USER_TASK')])}),
					}),
					sort: z.tuple([z.object({field: z.literal('actorId'), order: z.literal('ASC')})]),
				}),
				successResponse: auditLogsResponse(createAuditLog({operationType: 'ASSIGN'})),
				failureResponse: auditLogsResponse(createAuditLog({operationType: 'DELETE'})),
			}),
		);
		const screen = await renderOperationsLog('?operationType=ASSIGN,CANCEL&entityType=USER_TASK&sort=actorId%2BASC');

		await expect.element(screen.getByRole('cell', {name: 'Assign'})).toBeVisible();
	});

	it('should request the operations sorted by a clicked column', async ({worker}) => {
		worker.use(mockQueryAuditLogsEndpoint({successResponse: auditLogsResponse(createAuditLog())}));
		const screen = await renderOperationsLog();
		await expect.element(screen.getByRole('cell', {name: 'Create'})).toBeVisible();

		worker.use(
			mockQueryAuditLogsEndpoint({
				schema: z.object({sort: z.tuple([z.object({field: z.literal('entityType'), order: z.literal('DESC')})])}),
				successResponse: auditLogsResponse(createAuditLog({operationType: 'RESOLVE'})),
				failureResponse: auditLogsResponse(createAuditLog()),
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Entity Type'}));

		await expect.element(screen.getByRole('cell', {name: 'Resolve'})).toBeVisible();
	});

	it('should filter the operations by operation type', async ({worker}) => {
		worker.use(mockQueryAuditLogsEndpoint({successResponse: auditLogsResponse(createAuditLog())}));
		const screen = await renderOperationsLog();
		await expect.element(screen.getByRole('cell', {name: 'Create'})).toBeVisible();

		worker.use(
			mockQueryAuditLogsEndpoint({
				schema: z.object({filter: z.object({operationType: z.object({$in: z.tuple([z.literal('SUSPEND')])})})}),
				successResponse: auditLogsResponse(createAuditLog({operationType: 'SUSPEND'})),
				failureResponse: auditLogsResponse(createAuditLog()),
			}),
		);
		await userEvent.click(screen.getByRole('combobox', {name: /Operation type/}));
		await userEvent.click(screen.getByRole('option', {name: 'Suspend'}));

		await expect.element(screen.getByRole('cell', {name: 'Suspend'})).toBeVisible();
	});

	it('should open the details of an operation', async ({worker}) => {
		worker.use(mockQueryAuditLogsEndpoint({successResponse: auditLogsResponse(createAuditLog())}));
		const screen = await renderOperationsLog();

		await userEvent.click(screen.getByRole('button', {name: 'Open details'}));

		await expect.element(screen.getByRole('dialog')).toBeVisible();
	});

	it('should show an empty message when the instance has no operations', async ({worker}) => {
		worker.use(mockQueryAuditLogsEndpoint({successResponse: auditLogsResponse()}));
		const screen = await renderOperationsLog();

		await expect.element(screen.getByText('No operations yet')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: /Operation type/})).toBeVisible();
	});

	it('should show an error message when the operations cannot be fetched', async ({worker}) => {
		worker.use(
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderOperationsLog();

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
	});

	it('should replace the listed operations with an error message when a changed filter fails to load', async ({
		worker,
	}) => {
		worker.use(mockQueryAuditLogsEndpoint({successResponse: auditLogsResponse(createAuditLog())}));
		const screen = await renderOperationsLog();
		await expect.element(screen.getByRole('cell', {name: 'Create'})).toBeVisible();

		worker.use(
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		await userEvent.click(screen.getByRole('combobox', {name: /Operation type/}));
		await userEvent.click(screen.getByRole('option', {name: 'Suspend'}));

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Create'})).not.toBeInTheDocument();
	});

	it('should ask for a single element instance when the selected element has several', async ({worker}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([], 3)),
			}),
		);
		const screen = await renderOperationsLog('?elementId=user-task');

		await expect
			.element(
				screen.getByText('To view the Operations Log, select a single Element Instance in the Instance History.'),
			)
			.toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: /Operation type/})).not.toBeInTheDocument();
	});
});
