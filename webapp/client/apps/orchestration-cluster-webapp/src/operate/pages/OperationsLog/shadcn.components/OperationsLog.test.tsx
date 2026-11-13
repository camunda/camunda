/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {format, parseISO} from 'date-fns';
import {stringifySearchWith, useSearch} from '@tanstack/react-router';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {HttpResponse} from 'msw';
import {userEvent} from 'vitest/browser';
import {Toaster, toast} from '@camunda/design-system';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {Notifications} from '#/shared/notifications/shadcn.components/Notifications';
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
import {operationsLogSearchSchema, type OperationsLogSearch} from '../operationsLog.schema';
import {OperationsLog} from './OperationsLog';

const PROCESS_DEFINITIONS = HttpResponse.json(
	createQueryProcessDefinitionsResponse({
		items: [createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process', version: 1})],
	}),
);

const NO_DECISION_DEFINITIONS = HttpResponse.json(createQueryDecisionDefinitionsResponse());
const OPERATIONS_LOG_PATH = '/operate-preview/operations-log';
const AUDIT_LOG_ERROR = new HttpResponse(null, {status: 500});
const DECISION_LOOKUP_ERROR = new HttpResponse(null, {status: 503});
const DECISION_LOG = createAuditLog({
	auditLogKey: 'decision-log',
	entityKey: '888',
	entityType: 'DECISION',
	operationType: 'EVALUATE',
	decisionDefinitionKey: '888',
});
const DECISION_DEFINITIONS = HttpResponse.json(
	createQueryDecisionDefinitionsResponse({
		items: [createDecisionDefinition({decisionDefinitionKey: '888', name: 'Invoice Decision'})],
	}),
);

function renderPage({search = {}}: {search?: Partial<OperationsLogSearch>} = {}) {
	return renderWithRouter(
		() => {
			const routeSearch = operationsLogSearchSchema.parse(useSearch({strict: false}));
			return (
				<>
					<OperationsLog {...routeSearch} />
					<Toaster />
					<Notifications />
				</>
			);
		},
		{
			path: OPERATIONS_LOG_PATH,
			initialEntry: `${OPERATIONS_LOG_PATH}${stringifySearchWith(JSON.stringify)(search)}`,
		},
	);
}

describe('<OperationsLog />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
		notificationsStore.reset();
		toast.dismiss();
	});

	describe('audit log read states', () => {
		it('should keep audit rows and links available when decision names fail on first load, then recover', async ({
			worker,
		}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_LOOKUP_ERROR}),
				mockQueryAuditLogsEndpoint({
					successResponse: HttpResponse.json(
						createQueryAuditLogsResponse({
							items: [DECISION_LOG],
							page: {totalItems: 51, hasMoreTotalItems: true},
						}),
					),
				}),
			);

			const screen = await renderPage();

			await expect
				.element(screen.getByText("Couldn't load decision names. Audit logs are still available."))
				.toBeVisible();
			await expect.element(screen.getByRole('heading', {name: 'Operations Log - 51+ results'})).toBeVisible();
			await expect.element(screen.getByRole('link', {name: 'View decision instance 888'})).toBeVisible();
			await expect.element(screen.getByText('888', {exact: true})).toBeVisible();
			await expect.element(screen.getByText("Couldn't fetch audit logs")).not.toBeInTheDocument();
			await expect.element(screen.getByRole('button', {name: 'Retry decision names'})).toBeVisible();

			worker.use(mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}));
			await userEvent.click(screen.getByRole('button', {name: 'Retry decision names'}));

			await expect.element(screen.getByText('Invoice Decision')).toBeVisible();
			await expect
				.element(screen.getByText("Couldn't load decision names. Audit logs are still available."))
				.not.toBeInTheDocument();
		});

		it('should expose a cached decision-name refetch failure on returning to the page and recover', async ({
			worker,
		}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({
					successResponse: HttpResponse.json(createQueryAuditLogsResponse({items: [DECISION_LOG]})),
				}),
			);

			const screen = await renderPage();
			await expect.element(screen.getByText('Invoice Decision')).toBeVisible();

			worker.use(mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_LOOKUP_ERROR}));
			window.dispatchEvent(new Event('visibilitychange'));

			await expect
				.element(screen.getByText("Couldn't load decision names. Audit logs are still available."))
				.toBeVisible();
			await expect.element(screen.getByRole('heading', {name: 'Operations Log - 1 result'})).toBeVisible();
			await expect.element(screen.getByRole('link', {name: 'View decision instance 888'})).toBeVisible();
			await expect.element(screen.getByText("Couldn't fetch audit logs")).not.toBeInTheDocument();

			worker.use(
				mockQueryDecisionDefinitionsEndpoint({
					successResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse()),
				}),
			);
			await userEvent.click(screen.getByRole('button', {name: 'Retry decision names'}));

			await expect
				.element(screen.getByText("Couldn't load decision names. Audit logs are still available."))
				.not.toBeInTheDocument();
			await expect.element(screen.getByText('Invoice Decision')).not.toBeInTheDocument();
			await expect.element(screen.getByRole('link', {name: 'View decision instance 888'})).toBeVisible();
		});

		it('should not claim audit logs are available when both searches fail', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: DECISION_LOOKUP_ERROR}),
				mockQueryAuditLogsEndpoint({successResponse: AUDIT_LOG_ERROR}),
			);

			const screen = await renderPage();

			await expect.element(screen.getByRole('alert')).toMatchTextContent("Couldn't fetch audit logs");
			await expect
				.element(screen.getByText("Couldn't load decision names. Audit logs are still available."))
				.not.toBeInTheDocument();
			await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Retry decision names'})).not.toBeInTheDocument();

			worker.use(
				mockQueryAuditLogsEndpoint({
					successResponse: HttpResponse.json(createQueryAuditLogsResponse({items: [DECISION_LOG]})),
				}),
			);
			await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

			await expect.element(screen.getByRole('link', {name: 'View decision instance 888'})).toBeVisible();
			await expect
				.element(screen.getByText("Couldn't load decision names. Audit logs are still available."))
				.toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Retry decision names'})).toBeVisible();
		});

		it('should show loading rather than empty until the first audit response succeeds', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({
					successResponse: HttpResponse.json(createQueryAuditLogsResponse()),
					delay: 'infinite',
				}),
			);

			const screen = await renderPage();

			await expect.element(screen.getByRole('table', {name: 'Operations log'})).toBeVisible();
			await expect.element(screen.getByText('No operation log items yet')).not.toBeInTheDocument();
			await expect.element(screen.getByText('No operations log found')).not.toBeInTheDocument();
		});

		it('should offer sorting only once the first audit response has arrived', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({
					successResponse: HttpResponse.json(createQueryAuditLogsResponse({items: [DECISION_LOG]})),
					delay: 1000,
				}),
			);

			const screen = await renderPage();

			await expect.element(screen.getByRole('table', {name: 'Operations log'})).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Operation type'})).not.toBeInTheDocument();
			await expect.element(screen.getByRole('button', {name: 'Operation type'})).toBeVisible();
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

			const screen = await renderPage();
			await expect.element(screen.getByText('No operation log items yet')).toBeVisible();

			worker.use(
				mockQueryAuditLogsEndpoint({
					successResponse: HttpResponse.json(createQueryAuditLogsResponse()),
					delay: 'infinite',
				}),
			);
			await userEvent.fill(screen.getByRole('textbox', {name: 'Actor'}), 'unknown');

			await expect.element(screen.getByText('No operation log items yet')).not.toBeInTheDocument();
			await expect.element(screen.getByRole('table', {name: 'Operations log'})).toBeVisible();
			await expect.element(screen.getByText('No operations log found')).not.toBeInTheDocument();
		});

		it('should notify a failing audit log poll once until it recovers', async ({worker}) => {
			vi.useFakeTimers({
				toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'],
				shouldAdvanceTime: true,
			});
			try {
				worker.use(
					mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
					mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
					mockQueryAuditLogsEndpoint({successResponse: AUDIT_LOG_ERROR}),
				);
				const screen = await renderPage();
				const fetchFailedToast = screen
					.getByRole('region', {name: /notifications/i})
					.getByText("Couldn't fetch audit logs");

				await expect.element(fetchFailedToast).toBeVisible();
				await vi.advanceTimersByTimeAsync(20_000);
				await expect.element(fetchFailedToast).not.toBeInTheDocument();

				worker.use(mockQueryAuditLogsEndpoint({successResponse: HttpResponse.json(createQueryAuditLogsResponse())}));
				await expect.element(screen.getByText('No operation log items yet'), {timeout: 6000}).toBeVisible();

				worker.use(mockQueryAuditLogsEndpoint({successResponse: AUDIT_LOG_ERROR}));
				await vi.advanceTimersByTimeAsync(5_000);

				await expect.element(fetchFailedToast).toBeVisible();
			} finally {
				vi.useRealTimers();
			}
		});

		it('should show an initial fetch error with a notification and recover on retry', async ({worker}) => {
			worker.use(
				mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
				mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
				mockQueryAuditLogsEndpoint({successResponse: AUDIT_LOG_ERROR}),
			);

			const screen = await renderPage();

			await expect.element(screen.getByRole('alert')).toMatchTextContent("Couldn't fetch audit logs");
			await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
			await expect.element(screen.getByText('No operation log items yet')).not.toBeInTheDocument();
			await expect
				.element(screen.getByRole('region', {name: /notifications/i}).getByText("Couldn't fetch audit logs"))
				.toBeVisible();

			worker.use(
				mockQueryAuditLogsEndpoint({
					successResponse: HttpResponse.json(
						createQueryAuditLogsResponse({items: [createAuditLog({auditLogKey: 'recovered'})]}),
					),
				}),
			);
			await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

			await expect.element(screen.getByRole('alert')).not.toBeInTheDocument();
			await expect.element(screen.getByRole('heading', {name: 'Operations Log - 1 result'})).toBeVisible();
		});

		it.for([
			{
				description: 'cached rows',
				items: [createAuditLog({auditLogKey: 'cached', entityKey: '42'})],
				expectedText: 'Operations Log - 1 result',
			},
			{description: 'a cached empty response', items: [], expectedText: 'No operation log items yet'},
		])(
			'should show a failed refetch for $description with a notification and recover',
			async ({items, expectedText}, {worker}) => {
				worker.use(
					mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}),
					mockQueryDecisionDefinitionsEndpoint({successResponse: NO_DECISION_DEFINITIONS}),
					mockQueryAuditLogsEndpoint({
						successResponse: HttpResponse.json(createQueryAuditLogsResponse({items})),
					}),
				);
				const screen = await renderPage();

				await expect.element(screen.getByText(expectedText)).toBeVisible();

				worker.use(mockQueryAuditLogsEndpoint({successResponse: AUDIT_LOG_ERROR}));

				await expect.element(screen.getByRole('alert')).toMatchTextContent("Couldn't fetch audit logs");
				await expect.element(screen.getByRole('button', {name: 'Try again'})).toBeVisible();
				await expect.element(screen.getByText('No operation log items yet')).not.toBeInTheDocument();
				await expect.element(screen.getByRole('heading', {name: 'Operations Log', level: 1})).toBeVisible();
				await expect
					.element(screen.getByRole('region', {name: /notifications/i}).getByText("Couldn't fetch audit logs"))
					.toBeVisible();

				worker.use(
					mockQueryAuditLogsEndpoint({
						successResponse: HttpResponse.json(createQueryAuditLogsResponse({items})),
					}),
				);
				await userEvent.click(screen.getByRole('button', {name: 'Try again'}));

				await expect.element(screen.getByRole('alert')).not.toBeInTheDocument();
				await expect.element(screen.getByText(expectedText)).toBeVisible();
			},
		);

		it('should display approximate totals', async ({worker}) => {
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
			await expect.element(screen.getByRole('link', {name: 'View process instance 42'})).toBeVisible();
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
		await expect.element(screen.getByRole('heading', {name: 'Operations Log', level: 1})).toBeVisible();
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

		await expect.element(screen.getByRole('cell', {name: 'Operation type', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Entity type', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Entity key', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Parent entity', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Details', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Actor', exact: true})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'Date', exact: true})).toBeVisible();
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

	it('should render a link for each linkable entity and parent process instance', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionEndpoint({
				successResponse: HttpResponse.json(createGetProcessDefinitionResponse()),
			}),
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
								auditLogKey: '1',
								entityKey: '111',
								processInstanceKey: '111',
								entityType: 'PROCESS_INSTANCE',
								operationType: 'CANCEL',
							}),
							createAuditLog({
								auditLogKey: '2',
								entityKey: 'variable-1',
								processInstanceKey: '999',
								entityType: 'VARIABLE',
								operationType: 'UPDATE',
								processDefinitionKey: '2251799813685279',
							}),
							createAuditLog({
								auditLogKey: '3',
								entityType: 'BATCH',
								operationType: 'CANCEL',
								batchOperationKey: 'batch-123',
								batchOperationType: 'CANCEL_PROCESS_INSTANCE',
							}),
							createAuditLog({
								auditLogKey: '4',
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

		await expect.element(screen.getByRole('link', {name: 'View process instance 111'})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'View process instance 999'})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'View batch operation batch-123'})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'View decision instance 888'})).toBeVisible();
		await expect.element(screen.getByText('My Decision')).toBeVisible();
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
