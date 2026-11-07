/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {CaseStatusBadge} from './CaseStatusBadge';

describe('<CaseStatusBadge />', () => {
	it.for([
		{state: 'ACTIVE', label: 'Active'},
		{state: 'SUSPENDED', label: 'Suspended'},
		{state: 'COMPLETED', label: 'Completed'},
		{state: 'TERMINATED', label: 'Terminated'},
	] as const)('should show the $state state as $label', async ({state, label}) => {
		const screen = await render(<CaseStatusBadge state={state} hasIncident={false} />);

		await expect.element(screen.getByText(label)).toBeVisible();
	});

	it('should show an incident on an active case', async () => {
		const screen = await render(<CaseStatusBadge state="ACTIVE" hasIncident />);

		await expect.element(screen.getByText('Incident')).toBeVisible();
		await expect.element(screen.getByText('Active')).not.toBeInTheDocument();
	});
});
