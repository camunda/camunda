/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {OperationsLogResultIcon} from './OperationsLogResultIcon';

describe('<OperationsLogResultIcon />', () => {
	it.for([
		{state: 'SUCCESS', colorClassName: 'text-success-foreground-subtle'},
		{state: 'FAIL', colorClassName: 'text-danger-foreground-subtle'},
	] as const)('should render the $state icon with the expected styling', async ({state, colorClassName}) => {
		const screen = await render(<OperationsLogResultIcon state={state} data-testid="icon" />);

		const icon = screen.getByTestId('icon');
		await expect.element(icon).toBeVisible();
		await expect.element(icon).toHaveClass('shrink-0');
		await expect.element(icon).toHaveClass(colorClassName);
		await expect.element(icon).toHaveAttribute('aria-hidden', 'true');
		await expect.element(icon).toHaveAttribute('focusable', 'false');
	});
});
