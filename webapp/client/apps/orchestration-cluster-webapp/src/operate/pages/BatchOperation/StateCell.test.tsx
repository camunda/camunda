/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {createBatchOperationItem} from '#/shared-test-modules/api-mocks/batch-operations';
import {StateCell} from './StateCell';

describe('<StateCell />', () => {
	it('should render just the state indicator when there is no error', async () => {
		const screen = await render(<StateCell item={createBatchOperationItem({state: 'COMPLETED'})} />);

		await expect.element(screen.getByText('Completed')).toBeVisible();
	});

	it('should show the failure reason in a tooltip on hover for a failed item', async () => {
		const item = createBatchOperationItem({state: 'FAILED', errorMessage: 'Something went wrong'});
		const screen = await render(<StateCell item={item} />);

		await expect.element(screen.getByText('Failed')).toBeVisible();
		await expect.element(screen.getByText('Failure reason: Something went wrong')).not.toBeVisible();

		await userEvent.hover(screen.getByTestId('item-state-with-error'));

		await expect.element(screen.getByText('Failure reason: Something went wrong')).toBeVisible();
	});
});
