/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {createAuditLog} from '#/shared-test-modules/api-mocks/audit-logs';
import {it} from '#/vitest-modules/test-extend';
import {ActorIcon} from './ActorIcon';

describe('<ActorIcon />', () => {
	it('should render a user icon for a USER actor', async () => {
		const screen = await render(<ActorIcon auditLog={createAuditLog({actorType: 'USER'})} data-testid="icon" />);

		await expect.element(screen.getByTestId('icon')).toBeVisible();
	});

	it('should render a bot icon for a CLIENT actor', async () => {
		const screen = await render(<ActorIcon auditLog={createAuditLog({actorType: 'CLIENT'})} data-testid="icon" />);

		await expect.element(screen.getByTestId('icon')).toBeVisible();
	});

	it.for(['UNKNOWN', 'ANONYMOUS'] as const)('should render nothing for a %s actor', async (actorType) => {
		const screen = await render(<ActorIcon auditLog={createAuditLog({actorType})} data-testid="icon" />);

		await expect.element(screen.getByTestId('icon')).not.toBeInTheDocument();
	});
});
