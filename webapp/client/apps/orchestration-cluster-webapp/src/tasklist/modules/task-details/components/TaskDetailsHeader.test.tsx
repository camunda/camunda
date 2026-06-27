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
import {TaskDetailsHeader} from './TaskDetailsHeader';

const currentUser = createCurrentUser({username: 'demo'});

const baseProps = {
	taskName: 'Review invoice',
	processName: 'Invoice process',
	assignee: null,
	taskState: 'CREATED',
	user: currentUser,
	assignButton: <button>Assign to me</button>,
} satisfies React.ComponentProps<typeof TaskDetailsHeader>;

describe('<TaskDetailsHeader />', () => {
	it('should render task name and process name', async () => {
		const screen = await render(<TaskDetailsHeader {...baseProps} />);

		await expect.element(screen.getByText('Review invoice')).toBeVisible();
		await expect.element(screen.getByText('Invoice process')).toBeVisible();
	});

	it('should render completion label with assignee for COMPLETED task', async () => {
		const screen = await render(
			<TaskDetailsHeader {...baseProps} taskState="COMPLETED" assignee={currentUser.username} />,
		);

		await expect.element(screen.getByText(/Completed by/)).toBeVisible();
		await expect.element(screen.getByTestId('completion-label')).toBeVisible();
		await expect.element(screen.getByTestId('assignee')).toBeVisible();
	});

	it('should render "Completed" without assignee for COMPLETED task', async () => {
		const screen = await render(<TaskDetailsHeader {...baseProps} taskState="COMPLETED" assignee={null} />);

		await expect.element(screen.getByText('Completed')).toBeVisible();
		await expect.element(screen.getByTestId('completion-label')).toBeVisible();
	});

	it.for([{taskState: 'CREATED'}, {taskState: 'CANCELED'}, {taskState: 'FAILED'}] as const)(
		'should render assignee tag and assign button for $taskState task',
		async ({taskState}) => {
			const screen = await render(<TaskDetailsHeader {...baseProps} taskState={taskState} assignee={null} />);

			await expect.element(screen.getByTestId('assignee')).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Assign to me'})).toBeVisible();
		},
	);

	it.for([{taskState: 'UPDATING'}, {taskState: 'CANCELING'}] as const)(
		'should render transition loading text and assignee for $taskState task',
		async ({taskState}) => {
			const screen = await render(<TaskDetailsHeader {...baseProps} taskState={taskState} assignee="john.doe" />);

			await expect.element(screen.getByTestId('assignee')).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'Assign to me'})).not.toBeInTheDocument();
		},
	);

	it('should render only assignee for COMPLETING task', async () => {
		const screen = await render(
			<TaskDetailsHeader {...baseProps} taskState="COMPLETING" assignee={currentUser.username} />,
		);

		await expect.element(screen.getByTestId('assignee')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Assign to me'})).not.toBeInTheDocument();
	});

	it('should render only the assign button for ASSIGNING task', async () => {
		const screen = await render(<TaskDetailsHeader {...baseProps} taskState="ASSIGNING" />);

		await expect.element(screen.getByRole('button', {name: 'Assign to me'})).toBeVisible();
	});

	it('should render only transition loading text for CREATING task', async () => {
		const screen = await render(<TaskDetailsHeader {...baseProps} taskState="CREATING" />);

		await expect.element(screen.getByRole('button', {name: 'Assign to me'})).not.toBeInTheDocument();
	});
});
