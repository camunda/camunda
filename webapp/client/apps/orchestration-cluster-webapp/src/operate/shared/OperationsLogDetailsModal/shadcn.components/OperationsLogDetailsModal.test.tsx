/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {format, parseISO} from 'date-fns';
import {describe, expect} from 'vitest';
import {createAuditLog} from '#/shared-test-modules/api-mocks/audit-logs';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {it} from '#/vitest-modules/test-extend';
import {OperationsLogDetailsModal} from './OperationsLogDetailsModal';

const OPERATE_ROOT_PATH = '/operate';

function renderModal(auditLog: ReturnType<typeof createAuditLog>, basepath = '') {
	return renderWithRouter(() => <OperationsLogDetailsModal isOpen onClose={() => {}} auditLog={auditLog} />, {
		path: OPERATE_ROOT_PATH,
		basepath,
		initialEntry: `${basepath}${OPERATE_ROOT_PATH}`,
	});
}

describe('<OperationsLogDetailsModal />', () => {
	it('should render the status, actor, entity key and date rows', async () => {
		const auditLog = createAuditLog({
			operationType: 'CREATE',
			entityType: 'USER_TASK',
			result: 'SUCCESS',
			actorId: 'demo',
			actorType: 'USER',
			timestamp: '2024-01-01T10:00:00.000Z',
		});

		const screen = await renderModal(auditLog);

		await expect.element(screen.getByRole('dialog')).toBeVisible();
		await expect.element(screen.getByText('Status')).toBeVisible();
		await expect.element(screen.getByText('Actor')).toBeVisible();
		await expect.element(screen.getByText('demo').first()).toBeVisible();
		await expect.element(screen.getByText(format(parseISO(auditLog.timestamp), 'yyyy-MM-dd HH:mm:ss'))).toBeVisible();
	});

	it('should link to the user task using the entity key', async () => {
		const auditLog = createAuditLog({
			entityType: 'USER_TASK',
			entityKey: 'user-task-123',
		});

		const screen = await renderModal(auditLog);

		await expect
			.element(screen.getByRole('link', {name: 'View user task user-task-123'}))
			.toHaveAttribute('href', '/tasklist/user-task-123');
	});

	it('should show a link to the batch operation when the audit log is part of a batch', async () => {
		const auditLog = createAuditLog({
			entityType: 'USER_TASK',
			batchOperationKey: 'batch-123',
		});

		const screen = await renderModal(auditLog);

		await expect.element(screen.getByRole('dialog')).toMatchTextContent('This operation is part of a batch.');
		await expect
			.element(screen.getByRole('link', {name: 'View batch operation details.'}))
			.toHaveAttribute('href', '/operate/batch-operations/batch-123');
	});

	it('should render the applied-to section for BATCH entity types', async () => {
		const auditLog = createAuditLog({
			entityType: 'BATCH',
			operationType: 'CANCEL',
			batchOperationKey: 'batch-456',
			batchOperationType: 'CANCEL_PROCESS_INSTANCE',
		});

		const screen = await renderModal(auditLog);

		await expect.element(screen.getByText('Applied to:')).toBeVisible();
		await expect.element(screen.getByText(/process instances/)).toBeVisible();
	});

	it.for([
		{basepath: '', expectedPathPrefix: ''},
		{basepath: '/camunda', expectedPathPrefix: '/camunda'},
	])(
		'should render a process instance entity link with the correct basepath "$basepath"',
		async ({basepath, expectedPathPrefix}) => {
			const auditLog = createAuditLog({
				entityType: 'PROCESS_INSTANCE',
				entityKey: '2251799813685250',
				processInstanceKey: '2251799813685250',
				processDefinitionId: 'order-process',
			});

			const screen = await renderModal(auditLog, basepath);

			await expect
				.element(screen.getByRole('link', {name: 'View process instance 2251799813685250'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/processes/2251799813685250`);
		},
	);

	it.for([
		{basepath: '', expectedPathPrefix: ''},
		{basepath: '/camunda', expectedPathPrefix: '/camunda'},
	])(
		'should render a parent process instance link with the correct basepath "$basepath"',
		async ({basepath, expectedPathPrefix}) => {
			const auditLog = createAuditLog({
				entityType: 'VARIABLE',
				entityKey: 'variable-1',
				processInstanceKey: '2251799813685250',
				processDefinitionId: 'order-process',
			});

			const screen = await renderModal(auditLog, basepath);

			await expect
				.element(screen.getByRole('link', {name: 'View process instance 2251799813685250'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/processes/2251799813685250`);
		},
	);

	it.for([
		{basepath: '', expectedPathPrefix: ''},
		{basepath: '/camunda', expectedPathPrefix: '/camunda'},
	])(
		'should render a BATCH entity key link with the correct basepath "$basepath"',
		async ({basepath, expectedPathPrefix}) => {
			const auditLog = createAuditLog({
				entityType: 'BATCH',
				operationType: 'CANCEL',
				batchOperationKey: 'batch-456',
				batchOperationType: 'CANCEL_PROCESS_INSTANCE',
			});

			const screen = await renderModal(auditLog, basepath);

			// Both the entity key row and the "Applied to" section render a link with this same
			// aria-label; `.first()` targets the entity key row's link.
			await expect
				.element(screen.getByRole('link', {name: 'View batch operation batch-456'}).first())
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/batch-operations/batch-456`);
		},
	);

	it.for([
		{basepath: '', expectedPathPrefix: ''},
		{basepath: '/camunda', expectedPathPrefix: '/camunda'},
	])(
		'should render an evaluated decision entity key link with the correct basepath "$basepath"',
		async ({basepath, expectedPathPrefix}) => {
			const auditLog = createAuditLog({
				entityType: 'DECISION',
				operationType: 'EVALUATE',
				entityKey: 'decision-instance-123',
				decisionDefinitionId: 'approve-order',
			});

			const screen = await renderModal(auditLog, basepath);

			await expect
				.element(screen.getByRole('link', {name: 'View decision instance decision-instance-123'}))
				.toHaveAttribute('href', `${expectedPathPrefix}/operate/decisions/decision-instance-123`);
		},
	);

	it('should render the resource key detail row for RESOURCE entity types', async () => {
		const auditLog = createAuditLog({
			entityType: 'RESOURCE',
			resourceKey: 'resource-789',
		});

		const screen = await renderModal(auditLog);

		await expect.element(screen.getByText('Details:')).toBeVisible();
		await expect.element(screen.getByText('Resource key')).toBeVisible();
		await expect.element(screen.getByText('resource-789')).toBeVisible();
	});

	it('should render the agent row when the audit log has an agent element id', async () => {
		const auditLog = createAuditLog({
			agentElementId: 'order-approval-agent',
		});

		const screen = await renderModal(auditLog);

		await expect.element(screen.getByText('order-approval-agent')).toBeVisible();
	});

	it('should render the MCP inbound channel row with the MCP icon', async () => {
		const auditLog = createAuditLog({
			inboundChannelType: 'MCP',
		});

		const screen = await renderModal(auditLog);

		await expect.element(screen.getByRole('dialog')).toMatchTextContent(/Inbound channel:\s*MCP/);
		await expect.element(screen.getByTestId('mcp-icon')).toBeVisible();
	});

	it('should render a non-MCP inbound channel row without the MCP icon', async () => {
		const auditLog = createAuditLog({
			inboundChannelType: 'REST',
		});

		const screen = await renderModal(auditLog);

		await expect.element(screen.getByRole('dialog')).toMatchTextContent(/Inbound channel:\s*REST/);
		await expect.element(screen.getByTestId('mcp-icon')).not.toBeInTheDocument();
	});

	it('should render the inbound channel tool name row when present', async () => {
		const auditLog = createAuditLog({
			inboundChannelType: 'MCP',
			inboundChannelToolName: 'approve-order',
		});

		const screen = await renderModal(auditLog);

		await expect.element(screen.getByRole('dialog')).toMatchTextContent(/Inbound channel tool name:\s*approve-order/);
	});
});
