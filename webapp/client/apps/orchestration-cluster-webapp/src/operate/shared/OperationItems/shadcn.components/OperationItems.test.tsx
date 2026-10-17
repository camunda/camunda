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
import {OperationItems} from './OperationItems';

describe('<OperationItems />', () => {
	it('should render children as inline flex list items', async () => {
		const screen = await render(
			<OperationItems>
				<li>First action</li>
				<li>Second action</li>
			</OperationItems>,
		);

		const list = screen.getByRole('list');

		await expect.element(list).toBeVisible();
		await expect.element(list).toHaveAttribute('class', 'inline-flex flex-row');
		await expect.element(screen.getByRole('listitem').nth(0)).toHaveTextContent('First action');
		await expect.element(screen.getByRole('listitem').nth(1)).toHaveTextContent('Second action');
	});

	it('should normalize children with React.Children.toArray', async () => {
		const screen = await render(
			<OperationItems>
				{false}
				{null}
				<li>Only action</li>
			</OperationItems>,
		);

		expect(screen.getByRole('listitem').elements()).toHaveLength(1);
		await expect.element(screen.getByRole('listitem')).toHaveTextContent('Only action');
	});
});
