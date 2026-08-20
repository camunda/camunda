/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect, vi} from 'vitest';
import {AsyncActionButton} from './AsyncActionButton';

describe('<AsyncActionButton />', () => {
	it('should render a button when status is inactive', async () => {
		const screen = await render(
			<AsyncActionButton status="inactive" buttonProps={{type: 'button'}}>
				Click me
			</AsyncActionButton>,
		);

		await expect.element(screen.getByRole('button', {name: 'Click me'})).toBeVisible();
	});

	it('should render have a loading state', async () => {
		const screen = await render(
			<AsyncActionButton status="active" inlineLoadingProps={{description: 'Loading...'}}>
				Click me
			</AsyncActionButton>,
		);

		await expect.element(screen.getByText('Loading...')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Click me'})).not.toBeInTheDocument();
	});

	it('should render a success statee', async () => {
		const screen = await render(
			<AsyncActionButton status="finished" inlineLoadingProps={{description: 'Done!'}}>
				Click me
			</AsyncActionButton>,
		);

		await expect.element(screen.getByText('Done!')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Click me'})).not.toBeInTheDocument();
	});

	it('should hide button', async () => {
		const screen = await render(
			<AsyncActionButton status="inactive" isHidden>
				Hidden button
			</AsyncActionButton>,
		);

		await expect.element(screen.getByText('Hidden button')).not.toBeVisible();
	});

	it('should call onError callback after a delay', async () => {
		vi.useFakeTimers();
		const onError = vi.fn();

		await render(
			<AsyncActionButton status="error" onError={onError}>
				Action
			</AsyncActionButton>,
		);

		expect(onError).not.toHaveBeenCalled();

		await vi.advanceTimersByTimeAsync(500);

		expect(onError).toHaveBeenCalledOnce();

		vi.useRealTimers();
	});

	it('should call onSuccess callback after a delay', async () => {
		vi.useFakeTimers();
		const onSuccess = vi.fn();

		await render(
			<AsyncActionButton status="finished" inlineLoadingProps={{onSuccess}}>
				Action
			</AsyncActionButton>,
		);

		expect(onSuccess).not.toHaveBeenCalled();

		await vi.advanceTimersByTimeAsync(500);

		expect(onSuccess).toHaveBeenCalledOnce();

		vi.useRealTimers();
	});

	it('should allow button configuration', async () => {
		const screen = await render(
			<AsyncActionButton status="inactive" buttonProps={{kind: 'primary', size: 'sm', disabled: true}}>
				Disabled button
			</AsyncActionButton>,
		);

		await expect.element(screen.getByRole('button', {name: 'Disabled button'})).toBeDisabled();
	});

	it('should render error state', async () => {
		const screen = await render(
			<AsyncActionButton status="error" inlineLoadingProps={{description: 'Failed'}}>
				Action
			</AsyncActionButton>,
		);

		await expect.element(screen.getByRole('button', {name: 'Action'})).not.toBeInTheDocument();
		await expect.element(screen.getByText('Failed')).toBeVisible();
	});
});
