/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {createAuditLog} from '#/shared-test-modules/api-mocks/audit-logs';
import {CellActor} from './CellActor';

describe('<CellActor />', () => {
	it('should show an actor icon tooltip on hover for USER actors', async () => {
		const item = createAuditLog({actorId: 'user1', actorType: 'USER'});

		const screen = await render(<CellActor item={item} />);

		await expect.element(screen.getByText('User', {exact: true})).not.toBeVisible();

		await userEvent.hover(screen.getByTestId('actor-icon'));

		await expect.element(screen.getByText('User', {exact: true})).toBeVisible();
	});

	it('should show an MCP tooltip with the tool name for MCP inbound channel audit logs', async () => {
		const item = createAuditLog({
			actorId: 'mcp-caller',
			actorType: 'ANONYMOUS',
			inboundChannelType: 'MCP',
			inboundChannelToolName: 'my-tool',
		});

		const screen = await render(<CellActor item={item} />);

		await expect.element(screen.getByText('my-tool')).not.toBeVisible();

		await userEvent.hover(screen.getByTestId('mcp-icon'));

		await expect.element(screen.getByText('my-tool')).toBeVisible();
	});

	it('should render a dash when the audit log has no actor', async () => {
		const item = createAuditLog({actorId: undefined});

		const screen = await render(<CellActor item={item} />);

		await expect.element(screen.getByText('-')).toBeVisible();
	});
});
