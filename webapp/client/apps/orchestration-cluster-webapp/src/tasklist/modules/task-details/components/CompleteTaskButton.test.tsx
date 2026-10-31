/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TooltipProvider} from '@camunda/design-system';
import {render} from 'vitest-browser-react';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {CompleteTaskButton} from './CompleteTaskButton';

describe('<CompleteTaskButton />', () => {
	it('should render the completion button', async () => {
		const screen = await render(
			<CompleteTaskButton
				status="inactive"
				isDisabled={false}
				isHidden={false}
				isSuspended={false}
				onClick={() => {}}
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'Complete Task'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Complete Task'})).toBeEnabled();
	});

	it('should call onClick', async () => {
		const onClick = vi.fn();
		const screen = await render(
			<CompleteTaskButton
				status="inactive"
				isDisabled={false}
				isHidden={false}
				isSuspended={false}
				onClick={onClick}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Complete Task'}));

		expect(onClick).toHaveBeenCalledOnce();
	});

	it('should disable completion button', async () => {
		const screen = await render(
			<CompleteTaskButton status="inactive" isDisabled isHidden={false} isSuspended={false} onClick={() => {}} />,
		);

		await expect.element(screen.getByRole('button', {name: 'Complete Task'})).toBeDisabled();
		await expect
			.element(screen.getByRole('button', {name: 'Complete Task'}))
			.toHaveAttribute('title', 'Task is not assigned');
	});

	it('should hide completion button', async () => {
		const screen = await render(
			<CompleteTaskButton status="inactive" isDisabled={false} isHidden isSuspended={false} onClick={() => {}} />,
		);

		await expect.element(screen.getByText('Complete Task')).not.toBeVisible();
	});

	it('should show the active completion state', async () => {
		const screen = await render(
			<CompleteTaskButton status="active" isDisabled={false} isHidden={false} isSuspended={false} onClick={() => {}} />,
		);

		await expect.element(screen.getByText('Completing task...')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Complete Task'})).not.toBeInTheDocument();
	});

	it('should show the successful completion state', async () => {
		const screen = await render(
			<CompleteTaskButton
				status="finished"
				isDisabled={false}
				isHidden={false}
				isSuspended={false}
				onClick={() => {}}
			/>,
		);

		await expect.element(screen.getByText('Completed')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Complete Task'})).not.toBeInTheDocument();
	});

	it('should show the failed completion state', async () => {
		const screen = await render(
			<CompleteTaskButton status="error" isDisabled={false} isHidden={false} isSuspended={false} onClick={() => {}} />,
		);

		await expect.element(screen.getByText('Completion failed')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Complete Task'})).not.toBeInTheDocument();
	});

	it('should disable completion of a suspended task and explain why', async () => {
		const onClick = vi.fn();
		const screen = await render(
			<CompleteTaskButton status="inactive" isDisabled={false} isHidden={false} isSuspended onClick={onClick} />,
			{wrapper: TooltipProvider},
		);
		const button = screen.getByRole('button', {name: 'Complete Task'});

		await expect.element(button).toBeDisabled();
		await expect.element(button).not.toHaveAttribute('title');

		await userEvent.hover(button, {force: true});

		await expect
			.element(
				screen.getByRole('tooltip', {name: 'This action is disabled because the process instance is suspended.'}),
			)
			.toBeVisible();
		expect(onClick).not.toHaveBeenCalled();
	});
});
