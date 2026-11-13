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

describe('<CellActor /> (design system)', () => {
	it('should show an actor tooltip on hover for USER actors', async () => {
		const item = createAuditLog({actorId: 'user1', actorType: 'USER'});

		const screen = await render(<CellActor item={item} />);

		await userEvent.hover(screen.getByRole('button', {name: 'User'}));

		await expect.element(screen.getByRole('tooltip').getByText('user1')).toBeVisible();
	});

	it('should show an MCP tooltip with the tool name for MCP inbound channel audit logs', async () => {
		const item = createAuditLog({
			actorId: 'mcp-caller',
			actorType: 'ANONYMOUS',
			inboundChannelType: 'MCP',
			inboundChannelToolName: 'my-tool',
		});

		const screen = await render(<CellActor item={item} />);

		await userEvent.hover(screen.getByRole('button', {name: 'MCP tool call'}));

		await expect.element(screen.getByRole('tooltip').getByText('my-tool')).toBeVisible();
	});

	it('should announce a missing actor', async () => {
		const item = createAuditLog({actorId: undefined});

		const screen = await render(<CellActor item={item} />);

		await expect.element(screen.getByText('No actor')).toBeInTheDocument();
	});
});
