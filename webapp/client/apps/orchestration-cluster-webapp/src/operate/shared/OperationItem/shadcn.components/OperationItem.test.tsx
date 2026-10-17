/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {OperationItem} from './OperationItem';

const operationCases = [
	{type: 'RESOLVE_INCIDENT', testId: 'retry-operation', title: 'Retry Instance 123'},
	{type: 'CANCEL_PROCESS_INSTANCE', testId: 'cancel-operation', title: 'Cancel Instance 123'},
	{type: 'SUSPEND_PROCESS_INSTANCE', testId: 'suspend-operation', title: 'Suspend Instance 123'},
	{type: 'RESUME_PROCESS_INSTANCE', testId: 'resume-operation', title: 'Resume Instance 123'},
	{type: 'ENTER_MODIFICATION_MODE', testId: 'enter-modification-mode', title: 'Modify Instance 123'},
] as const;

describe('<OperationItem />', () => {
	it.for(operationCases)('should render the $type icon button', async ({type, testId, title}) => {
		const screen = await render(<OperationItem type={type} onClick={vi.fn()} title={title} />);

		const button = screen.getByRole('button', {name: title, exact: true});

		await expect.element(button).toBeVisible();
		await expect.element(button).toHaveAttribute('data-size', 'default');
		await expect.element(screen.getByTestId(testId)).toBeVisible();
	});

	it.for([
		['xs', 'xs'],
		['sm', 'sm'],
		['default', 'default'],
		['lg', 'lg'],
	] as const)('should apply the %s size', async ([size, expectedSize]) => {
		const screen = await render(
			<OperationItem type="RESOLVE_INCIDENT" onClick={vi.fn()} title="Retry Instance 123" size={size} />,
		);

		await expect
			.element(screen.getByRole('button', {name: 'Retry Instance 123', exact: true}))
			.toHaveAttribute('data-size', expectedSize);
	});

	it('should execute the operation when clicked', async () => {
		const onClick = vi.fn();
		const screen = await render(<OperationItem type="RESOLVE_INCIDENT" onClick={onClick} title="Retry Instance 123" />);

		await userEvent.click(screen.getByRole('button', {name: 'Retry Instance 123', exact: true}));

		expect(onClick).toHaveBeenCalledOnce();
	});

	it('should preserve the disabled state', async () => {
		const screen = await render(
			<OperationItem type="CANCEL_PROCESS_INSTANCE" onClick={vi.fn()} title="Cancel Instance 123" disabled />,
		);

		const button = screen.getByRole('button', {name: 'Cancel Instance 123', exact: true});

		await expect.element(button).toBeDisabled();
	});
});
