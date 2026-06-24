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
import {AssigneeTag} from './AssigneeTag';

const currentUser = createCurrentUser({username: 'demo'});

describe('<AssigneeTag />', () => {
	it('should display "Unassigned" ', async () => {
		const screen = await render(<AssigneeTag currentUser={currentUser} assignee={null} />);

		await expect.element(screen.getByText('Unassigned')).toBeVisible();

		await screen.rerender(<AssigneeTag currentUser={currentUser} assignee={undefined} />);

		await expect.element(screen.getByText('Unassigned')).toBeVisible();
	});

	it('should display "Me"', async () => {
		const screen = await render(<AssigneeTag currentUser={currentUser} assignee="demo" isShortFormat />);

		await expect.element(screen.getByText('Me')).toBeVisible();
	});

	it('should display "Assigned to me"', async () => {
		const screen = await render(<AssigneeTag currentUser={currentUser} assignee="demo" isShortFormat={false} />);

		await expect.element(screen.getByText('Assigned to me')).toBeVisible();
	});

	it('should display the assignee username', async () => {
		const screen = await render(<AssigneeTag currentUser={currentUser} assignee="john.doe" isShortFormat />);

		await expect.element(screen.getByText('john.doe')).toBeVisible();
	});

	it('should display "Assigned to john.doe"', async () => {
		const screen = await render(<AssigneeTag currentUser={currentUser} assignee="john.doe" isShortFormat={false} />);

		await expect.element(screen.getByText('Assigned to john.doe')).toBeVisible();
	});
});
