/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TooltipProvider} from '@camunda/design-system';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
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
		const screen = await render(<AssigneeBadge currentUser={currentUser} assignee="demo" isShortFormat />);

		await expect.element(screen.getByText('Me')).toBeVisible();
	});

	it('should display "Assigned to me"', async () => {
		const screen = await render(<AssigneeBadge currentUser={currentUser} assignee="demo" isShortFormat={false} />);

		await expect.element(screen.getByText('Assigned to me')).toBeVisible();
	});

	it('should display the assignee username', async () => {
		const screen = await render(<AssigneeBadge currentUser={currentUser} assignee="john.doe" isShortFormat />);

		await expect.element(screen.getByText('john.doe')).toBeVisible();
	});

	it('should display "Assigned to john.doe"', async () => {
		const screen = await render(<AssigneeBadge currentUser={currentUser} assignee="john.doe" isShortFormat={false} />);

		await expect.element(screen.getByText('Assigned to john.doe')).toBeVisible();
	});

	it('should keep the native title and not truncate by default', async () => {
		const screen = await render(<AssigneeBadge currentUser={currentUser} assignee="john.doe" />);

		expect(screen.container.querySelector('[title]')).not.toBeNull();
		expect(screen.container.querySelector('.truncate')).toBeNull();
	});

	it('should truncate a long assignee and reveal it in a tooltip when truncate is set', async () => {
		const assignee = 'a.very.long.assignee.username@example-company.com';
		const screen = await render(
			<TooltipProvider>
				<div style={{display: 'flex', width: 100}}>
					<AssigneeBadge currentUser={currentUser} assignee={assignee} truncate />
				</div>
			</TooltipProvider>,
		);

		expect(screen.container.querySelector('[title]')).toBeNull();

		await userEvent.hover(screen.getByText(assignee));

		await expect.element(screen.getByRole('tooltip')).toHaveTextContent(assignee);
	});
});
