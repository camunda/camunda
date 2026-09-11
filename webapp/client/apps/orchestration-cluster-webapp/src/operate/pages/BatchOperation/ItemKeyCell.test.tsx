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
import {ItemKeyCell} from './ItemKeyCell';

describe('<ItemKeyCell />', () => {
	it('should render the fallback text when there is no associated item', async () => {
		const screen = await render(<ItemKeyCell itemKey="-1" fallbackText="No process instance" />);

		await expect.element(screen.getByText('No process instance')).toBeVisible();
		await expect.element(screen.getByRole('link')).not.toBeInTheDocument();
	});

	it('should render a link when a href is provided', async () => {
		const screen = await render(
			<ItemKeyCell
				itemKey="123"
				fallbackText="No process instance"
				href="/operate/processes/123"
				label="View process instance 123"
			/>,
		);

		const link = screen.getByRole('link', {name: 'View process instance 123'});
		await expect.element(link).toBeVisible();
		await expect.element(link).toHaveAttribute('href', '/operate/processes/123');
		await expect.element(link).toHaveTextContent('123');
	});

	it('should render plain text when no href is provided', async () => {
		const screen = await render(<ItemKeyCell itemKey="incident-key-1" fallbackText="No incident" />);

		await expect.element(screen.getByText('incident-key-1')).toBeVisible();
		await expect.element(screen.getByRole('link')).not.toBeInTheDocument();
	});
});
