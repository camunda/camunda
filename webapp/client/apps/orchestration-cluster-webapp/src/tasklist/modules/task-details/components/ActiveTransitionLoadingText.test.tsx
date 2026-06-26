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
import {ActiveTransitionLoadingText} from './ActiveTransitionLoadingText';

describe('<ActiveTransitionLoadingText />', () => {
	it('should render the updating message for UPDATING state', async () => {
		const screen = await render(<ActiveTransitionLoadingText taskState="UPDATING" />);

		await expect.element(screen.getByText('Please wait while the task is being updated...')).toBeVisible();
	});

	it('should render the canceling message for CANCELING state', async () => {
		const screen = await render(<ActiveTransitionLoadingText taskState="CANCELING" />);

		await expect.element(screen.getByText('The task is being cancelled. Please wait...')).toBeVisible();
	});

	it('should render the completing message for COMPLETING state', async () => {
		const screen = await render(<ActiveTransitionLoadingText taskState="COMPLETING" />);

		await expect.element(screen.getByText('Completing task...')).toBeVisible();
	});

	it.for([
		{taskState: 'CREATED'},
		{taskState: 'CREATING'},
		{taskState: 'ASSIGNING'},
		{taskState: 'COMPLETED'},
		{taskState: 'CANCELED'},
		{taskState: 'FAILED'},
	] as const)('should render nothing for $taskState state', async ({taskState}) => {
		const screen = await render(<ActiveTransitionLoadingText taskState={taskState} />);

		await expect.element(screen.getByText('Please wait while the task is being updated...')).not.toBeInTheDocument();
		await expect.element(screen.getByText('The task is being cancelled. Please wait...')).not.toBeInTheDocument();
		await expect.element(screen.getByText('Completing task...')).not.toBeInTheDocument();
	});
});
