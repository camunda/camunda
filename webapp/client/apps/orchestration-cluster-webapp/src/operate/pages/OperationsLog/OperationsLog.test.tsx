/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {format, parseISO} from 'date-fns';
import {useState} from 'react';
import {afterEach, beforeEach, describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {
	mockGetProcessDefinitionEndpoint,
	mockQueryAuditLogsEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryProcessDefinitionsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createAuditLog, createQueryAuditLogsResponse} from '#/shared-test-modules/api-mocks/audit-logs';
import {
	createGetProcessDefinitionResponse,
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import type {OperationsLogSearch} from './operationsLog.schema';
import {OperationsLog} from './OperationsLog';

const PROCESS_DEFINITIONS = HttpResponse.json(
	createQueryProcessDefinitionsResponse({
		items: [createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process', version: 1})],
	}),
);

const NO_DECISION_DEFINITIONS = HttpResponse.json(createQueryDecisionDefinitionsResponse());
const OPERATIONS_LOG_PATH = '/operate/operations-log';
const AUDIT_LOG_ERROR = new HttpResponse(null, {status: 500});

function renderPage({
	search,
	basepath = '',
}: {
	search?: Partial<OperationsLogSearch>;
	basepath?: string;
} = {}) {
	return renderWithRouter(() => <OperationsLog {...(search ?? {})} />, {
		path: OPERATIONS_LOG_PATH,
		basepath,
		initialEntry: `${basepath}${OPERATIONS_LOG_PATH}`,
	});
}

describe('<OperationsLog />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
		notificationsStore.reset();
	});

	describe('audit log read states', () => {
		it('should show loading rather than empty until the first audit response succeeds', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({
					successResponse: HttpResponse.json(createQueryAuditLogsResponse()),
					delay: 1000,
				}),
			);

			const screen = await renderPage();

			await expect.element(screen.getByRole('table')).toBeVisible();
			await expect.element(screen.getByText('No operation log items yet')).not.toBeInTheDocument();
			await expect.element(screen.getByText('No operations log found')).not.toBeInTheDocument();
			await expect.element(screen.getByText('No operation log items yet')).toBeVisible();
		});

		it('should show the filtered empty message only after a successful empty response', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}),
			);

			const screen = await renderPage({search: {actorId: 'unknown'}});

			await expect.element(screen.getByText('No operations log found')).toBeVisible();
			await expect.element(screen.getByText('No operation log items yet')).not.toBeInTheDocument();
		});

		it('should not show a previous empty result while a new filter is loading', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}),
			);

			function SearchChange() {
				const [search, setSearch] = useState<OperationsLogSearch>({});
				return (
					<>
						<button type="button" onClick={() => setSearch({actorId: 'unknown'})}>
							Change filter
						</button>
						<OperationsLog {...search} />
					</>
				);
			}

			const screen = await renderWithRouter(SearchChange, {path: OPERATIONS_LOG_PATH});
			await expect.element(screen.getByText('No operation log items yet')).toBeVisible();

			worker.use(
				mockQueryAuditLogsEndpoint({
					successResponse: HttpResponse.json(createQueryAuditLogsResponse()),
					delay: 1000,
				}),
			);
			await userEvent.click(screen.getByRole('button', {name: 'Change filter'}));

			await expect.element(screen.getByRole('table')).toBeVisible();
			await expect.element(screen.getByText('No operation log items yet')).not.toBeInTheDocument();
			await expect.element(screen.getByText('No operations log found')).not.toBeInTheDocument();
			await expect.element(screen.getByText('No operations log found')).toBeVisible();
		});

		it('should show an initial fetch error and recover on retry without a notification', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({successResponse: AUDIT_LOG_ERROR}),
			);

			const screen = await renderPage();

			await expect.element(screen.getByTestId('operations-log-table').getByRole('alert')).toBeVisible();
			await expect.element(screen.getByText("Couldn't fetch audit logs")).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
			await expect.element(screen.getByText('No operation log items yet')).not.toBeInTheDocument();
			expect(notificationsStore.notifications).toHaveLength(0);

			worker.use(
				mockQueryAuditLogsEndpoint({
					successResponse: HttpResponse.json(
						createQueryAuditLogsResponse({items: [createAuditLog({auditLogKey: 'recovered'})]}),
					),
				}),
			);
			await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

			await expect.element(screen.getByText("Couldn't fetch audit logs")).not.toBeInTheDocument();
			await expect.element(screen.getByRole('heading', {name: 'Operations Log - 1 result'})).toBeVisible();
			expect(notificationsStore.notifications).toHaveLength(0);
		});

		it.for([
			{description: 'cached rows', items: [createAuditLog({auditLogKey: 'cached', entityKey: '42'})]},
			{description: 'a cached empty response', items: []},
		])(
			'should show a failed refetch for $description and recover without duplicate notifications',
			async ({items}, {worker}) => {
				worker.use(
					mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
					mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
					mockQueryAuditLogsEndpoint({
						successResponse: HttpResponse.json(createQueryAuditLogsResponse({items})),
					}),
				);

				const screen = await renderPage();
				const {queryClient} = screen;

				if (items.length > 0) {
					await expect.element(screen.getByRole('heading', {name: 'Operations Log - 1 result'})).toBeVisible();
				} else {
					await expect.element(screen.getByText('No operation log items yet')).toBeVisible();
				}

				worker.use(mockQueryAuditLogsEndpoint({successResponse: AUDIT_LOG_ERROR}));
				await queryClient.invalidateQueries({queryKey: ['operationsLogAuditLogs']});

				await expect.element(screen.getByTestId('operations-log-table').getByRole('alert')).toBeVisible();
				await expect.element(screen.getByText("Couldn't fetch audit logs")).toBeVisible();
				await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
				await expect.element(screen.getByText('No operation log items yet')).not.toBeInTheDocument();
				await expect.element(screen.getByRole('heading', {name: 'Operations Log'})).toBeVisible();
				expect(notificationsStore.notifications).toHaveLength(0);

				await userEvent.click(screen.getByRole('button', {name: 'Try again'}));
				await expect.element(screen.getByText("Couldn't fetch audit logs")).toBeVisible();
				expect(notificationsStore.notifications).toHaveLength(0);

				worker.use(
					mockQueryAuditLogsEndpoint({
						successResponse: HttpResponse.json(createQueryAuditLogsResponse({items})),
					}),
				);
				await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

				await expect.element(screen.getByText("Couldn't fetch audit logs")).not.toBeInTheDocument();
				if (items.length > 0) {
					await expect.element(screen.getByRole('heading', {name: 'Operations Log - 1 result'})).toBeVisible();
				} else {
					await expect.element(screen.getByText('No operation log items yet')).toBeVisible();
				}
				expect(notificationsStore.notifications).toHaveLength(0);
			},
		);

		it('should display approximate totals without changing existing row links', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({
					successResponse: HttpResponse.json(
						createQueryAuditLogsResponse({
							items: [createAuditLog({entityType: 'PROCESS_INSTANCE', entityKey: '42', processInstanceKey: '42'})],
							page: {totalItems: 10000, hasMoreTotalItems: true},
						}),
					),
				}),
			);

			const screen = await renderPage();

			await expect.element(screen.getByRole('heading', {name: 'Operations Log - 10000+ results'})).toBeVisible();
			await expect
				.element(screen.getByRole('link', {name: 'View process instance 42'}))
				.toHaveAttribute('href', '/operate/processes/42');
		});
	});

	it('should render the filters panel and the instances table header', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText('Process', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('Operations Log')).toBeVisible();
	});

	it('should render all table column headers', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuditLogsResponse({items: [createAuditLog({auditLogKey: '123'})]}),
				),
			}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByRole('columnheader', {name: /operation type/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /entity type/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /entity key/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /parent entity/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /details/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /actor/i})).toBeVisible();
		await expect.element(screen.getByRole('columnheader', {name: /date/i})).toBeVisible();
	});

	it('should render the empty state without filters', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText('No operation log items yet')).toBeVisible();
	});

	it('should render the empty state with a filter applied', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}),
		);

		const screen = await renderPage({search: {actorId: 'demo'}});

		await expect.element(screen.getByText('No operations log found')).toBeVisible();
	});

	it('should render audit log rows with humanized operation and entity type labels', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuditLogsResponse({
						items: [
							createAuditLog({
								auditLogKey: '123',
								operationType: 'UPDATE',
								entityType: 'VARIABLE',
								result: 'SUCCESS',
								actorId: 'user1',
								actorType: 'ANONYMOUS',
								timestamp: '2024-01-01T12:30:45.000Z',
							}),
						],
					}),
				),
			}),
		);

		const screen = await renderPage();

		await expect.element(screen.getByText('Update')).toBeVisible();
		await expect.element(screen.getByText('Variable')).toBeVisible();
		await expect.element(screen.getByText('user1')).toBeVisible();
		await expect
			.element(screen.getByText(format(parseISO('2024-01-01T12:30:45.000Z'), 'yyyy-MM-dd HH:mm:ss')))
			.toBeVisible();
	});

	it('should render a batch operation link for BATCH entity types', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuditLogsResponse({
						items: [
							createAuditLog({
								auditLogKey: '789',
								entityType: 'BATCH',
								operationType: 'CANCEL',
								batchOperationKey: 'batch-123',
								batchOperationType: 'CANCEL_PROCESS_INSTANCE',
							}),
						],
					}),
				),
			}),
		);

		const screen = await renderPage();

		await expect
			.element(screen.getByRole('link', {name: 'View batch operation batch-123'}))
			.toHaveAttribute('href', '/operate/batch-operations/batch-123');
	});

	it.for([
		{basepath: '', expectedPathPrefix: ''},
		{basepath: '/camunda', expectedPathPrefix: '/camunda'},
	])(
		'should render a process instance entity link with the correct basepath "$basepath"',
		async ({basepath, expectedPathPrefix}, {worker}) => {
			worker.use(
				mockGetProcessDefinitionEndpoint({
					successResponse: HttpResponse.json(createGetProcessDefinitionResponse()),
				}),
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({
					successResponse: HttpResponse.json(
						createQueryAuditLogsResponse({
							items: [
								createAuditLog({
									auditLogKey: '123',
									entityKey: '999',
									processInstanceKey: '999',
									entityType: 'PROCESS_INSTANCE',
									operationType: 'CANCEL',
									processDefinitionKey: '2251799813685279',
								}),
							],
						}),
					),
				}),
			);

			const screen = await renderPage({basepath});

			await expect
				.element(screen.getByRole('link', {name: 'View process instance 999'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/processes/999`);
		},
	);

	it.for([
		{basepath: '', expectedPathPrefix: ''},
		{basepath: '/camunda', expectedPathPrefix: '/camunda'},
	])(
		'should render a parent process instance link with the correct basepath "$basepath"',
		async ({basepath, expectedPathPrefix}, {worker}) => {
			worker.use(
				mockGetProcessDefinitionEndpoint({
					successResponse: HttpResponse.json(createGetProcessDefinitionResponse()),
				}),
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({
					successResponse: HttpResponse.json(
						createQueryAuditLogsResponse({
							items: [
								createAuditLog({
									auditLogKey: '123',
									entityKey: 'variable-1',
									processInstanceKey: '999',
									entityType: 'VARIABLE',
									operationType: 'UPDATE',
									processDefinitionKey: '2251799813685279',
								}),
							],
						}),
					),
				}),
			);

			const screen = await renderPage({basepath});

			await expect
				.element(screen.getByRole('link', {name: 'View process instance 999'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/processes/999`);
		},
	);

	it('should render a decision instance link for DECISION entity types', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryDecisionDefinitionsResponse({
						items: [createDecisionDefinition({decisionDefinitionKey: '888', name: 'My Decision'})],
					}),
				),
			}),
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuditLogsResponse({
						items: [
							createAuditLog({
								auditLogKey: '123',
								entityKey: '888',
								entityType: 'DECISION',
								operationType: 'EVALUATE',
								decisionDefinitionKey: '888',
							}),
						],
					}),
				),
			}),
		);

		const screen = await renderPage();

		await expect
			.element(screen.getByRole('link', {name: 'View decision instance 888'}))
			.toHaveAttribute('href', '/operate/decisions/888');
	});

	it('should open the details modal when the comment button is clicked', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
			mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
			mockQueryAuditLogsEndpoint({
				successResponse: HttpResponse.json(
					createQueryAuditLogsResponse({items: [createAuditLog({auditLogKey: '123'})]}),
				),
			}),
		);

		const screen = await renderPage();

		await screen.getByRole('button', {name: /open details/i}).click();

		await expect.element(screen.getByRole('dialog')).toBeVisible();
	});

	describe('reset button', () => {
		it('is disabled at the default filter state', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}),
			);

			const screen = await renderPage();

			await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeDisabled();
		});

		it('is enabled once a filter is set via the URL', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}),
			);

			const screen = await renderPage({search: {actorId: 'demo-user'}});

			await expect.element(screen.getByRole('button', {name: 'Reset filters'})).not.toBeDisabled();
		});

		it('is enabled after typing into a filter field and disabled again after reset', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}),
			);

			const screen = await renderPage();
			const resetButton = screen.getByRole('button', {name: 'Reset filters'});

			await screen.getByLabelText('Actor').fill('demo-user');

			await expect.element(resetButton).not.toBeDisabled();

			await resetButton.click();

			await expect.element(resetButton).toBeDisabled();
			await expect.element(screen.getByLabelText('Actor')).toHaveValue('');
		});
	});
});
