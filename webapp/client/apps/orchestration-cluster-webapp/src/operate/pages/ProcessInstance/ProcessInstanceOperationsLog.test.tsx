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
import {http, HttpResponse} from 'msw';
import {z} from 'zod';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.11';
import {useRouterState} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetElementInstanceEndpoint,
	mockQueryAuditLogsEndpoint,
	mockQueryElementInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createAuditLog, createQueryAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {ProcessInstanceOperationsLog} from './ProcessInstanceOperationsLog';
import {processInstanceSearchSchema} from './processInstanceSearch';
import {processInstanceOperationsLogSearchSchema} from './processInstanceOperationsLog.schema';

const PROCESS_INSTANCE_ID = '2251799813685280';

afterEach(async () => {
	await cleanup();
	notificationsStore.reset();
});

function auditLogsResponse(...items: ReturnType<typeof createAuditLog>[]) {
	return HttpResponse.json(createQueryAuditLogsResponse({items}));
}

function createGate() {
	let open: () => void = () => {};
	const opened = new Promise<void>((resolve) => {
		open = resolve;
	});
	return {open, opened};
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
		path: '/operate/processes/$processInstanceId/operations-log',
		initialEntry: `/operate/processes/${PROCESS_INSTANCE_ID}/operations-log${search}`,
	});
}

describe('<ProcessInstanceOperationsLog />', () => {
	it('should list the non-admin operations of the process instance, newest first', async ({worker}) => {
		worker.use(
			mockQueryAuditLogsEndpoint({
				schema: z.object({
					filter: z
						.object({
							category: z.object({$neq: z.literal('ADMIN')}).strict(),
							processInstanceKey: z.literal(PROCESS_INSTANCE_ID),
						})
						.strict(),
					sort: z.tuple([z.object({field: z.literal('timestamp'), order: z.literal('DESC')})]),
					page: z.object({from: z.literal(0), limit: z.literal(50)}).strict(),
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
		await expect.element(screen.getByRole('cell', {name: 'jane'})).toBeVisible();
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
		await userEvent.click(screen.getByRole('columnheader', {name: 'Entity type'}));

		await expect.element(screen.getByRole('cell', {name: 'Resolve'})).toBeVisible();
	});

	it('should filter the operations by operation type and keep the filter in the URL', async ({worker}) => {
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
		await expect.poll(() => screen.router.state.location.search).toMatchObject({operationType: ['SUSPEND']});
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

		await expect.element(screen.getByText('No operations found for this instance')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: /Operation type/})).toBeVisible();
	});

	it('should show an error message and notify when the operations cannot be fetched', async ({worker}) => {
		worker.use(
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderOperationsLog();

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect
			.poll(() => notificationsStore.notifications.map(({title}) => title))
			.toContain("Couldn't fetch audit logs");
	});

	it('should replace the listed operations with an error message when a refetch fails', async ({worker}) => {
		worker.use(mockQueryAuditLogsEndpoint({successResponse: auditLogsResponse(createAuditLog())}));
		const screen = await renderOperationsLog();
		await expect.element(screen.getByRole('cell', {name: 'Create'})).toBeVisible();

		worker.use(
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		await screen.queryClient.invalidateQueries({queryKey: ['operationsLogAuditLogs']});

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Create'})).not.toBeInTheDocument();
	});

	it('should wait for the selected element instance instead of asking for a single one', async ({worker}) => {
		const gate = createGate();
		const elementInstance = createElementInstance({elementInstanceKey: '2251799813685299'});
		worker.use(
			http.get(endpoints.getElementInstance.getUrl({elementInstanceKey: ':elementInstanceKey'}), async () => {
				await gate.opened;
				return HttpResponse.json(elementInstance);
			}),
			mockQueryAuditLogsEndpoint({successResponse: auditLogsResponse(createAuditLog({operationType: 'MIGRATE'}))}),
		);
		const screen = await renderOperationsLog(
			`?elementId=user-task&elementInstanceKey=${elementInstance.elementInstanceKey}`,
		);

		await expect
			.element(
				screen.getByText('To view the Operations Log, select a single Element Instance in the Instance History.'),
			)
			.not.toBeInTheDocument();
		gate.open();

		await expect.element(screen.getByRole('cell', {name: 'Migrate'})).toBeVisible();
	});

	it('should show an error message when the selected element instance cannot be fetched', async ({worker}) => {
		worker.use(
			mockGetElementInstanceEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderOperationsLog('?elementId=user-task&elementInstanceKey=2251799813685299');

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
		await expect
			.element(
				screen.getByText('To view the Operations Log, select a single Element Instance in the Instance History.'),
			)
			.not.toBeInTheDocument();
	});

	it('should show an empty message for an element that has no instances', async ({worker}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([], 0)),
			}),
		);
		const screen = await renderOperationsLog('?elementId=user-task');

		await expect.element(screen.getByText('No operations found for this instance')).toBeVisible();
		await expect
			.element(
				screen.getByText('To view the Operations Log, select a single Element Instance in the Instance History.'),
			)
			.not.toBeInTheDocument();
	});

	it('should show an empty message for a placeholder instance', async () => {
		const screen = await renderOperationsLog('?elementId=user-task&elementInstanceKey=1&isPlaceholder=true');

		await expect.element(screen.getByText('No operations found for this instance')).toBeVisible();
	});

	it('should not show the empty message while a changed filter is loading', async ({worker}) => {
		const gate = createGate();
		worker.use(mockQueryAuditLogsEndpoint({successResponse: auditLogsResponse()}));
		const screen = await renderOperationsLog();
		await expect.element(screen.getByText('No operations found for this instance')).toBeVisible();

		worker.use(
			http.post(endpoints.queryAuditLogs.getUrl(), async () => {
				await gate.opened;
				return auditLogsResponse(createAuditLog({operationType: 'SUSPEND'}));
			}),
		);
		await userEvent.click(screen.getByRole('combobox', {name: /Operation type/}));
		await userEvent.click(screen.getByRole('option', {name: 'Suspend'}));

		await expect.element(screen.getByText('No operations found for this instance')).not.toBeInTheDocument();
		gate.open();

		await expect.element(screen.getByRole('cell', {name: 'Suspend'})).toBeVisible();
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
