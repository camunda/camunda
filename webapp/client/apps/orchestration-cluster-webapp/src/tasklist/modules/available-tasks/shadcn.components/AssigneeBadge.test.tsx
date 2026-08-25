/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {AssigneeBadge} from './AssigneeBadge';

const currentUser = createCurrentUser({username: 'demo'});

describe('<AssigneeBadge />', () => {
	it('should display "Unassigned" ', async () => {
		const screen = await render(<AssigneeBadge currentUser={currentUser} assignee={null} />);

		await expect.element(screen.getByText('Unassigned')).toBeVisible();

		await screen.rerender(<AssigneeBadge currentUser={currentUser} assignee={undefined} />);

		await expect.element(screen.getByText('Unassigned')).toBeVisible();
	});

	it('should display "Me"', async () => {
		const screen = await render(<AssigneeBadge currentUser={currentUser} assignee="demo" />);

		await expect.element(screen.getByText('Me')).toBeVisible();
	});

	it('should display the assignee username', async () => {
		const screen = await render(<AssigneeBadge currentUser={currentUser} assignee="john.doe" />);

		await expect.element(screen.getByText('john.doe')).toBeVisible();
	});
});
