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
import {DiagramShell} from './DiagramShell';

describe('<DiagramShell />', () => {
	it('should render children for content status', async () => {
		const screen = await render(<DiagramShell status="content">Diagram</DiagramShell>);

		await expect.element(screen.getByText('Diagram')).toBeVisible();
	});

	it('should render a spinner for loading status', async () => {
		const screen = await render(<DiagramShell status="loading">{null}</DiagramShell>);

		await expect.element(screen.getByRole('status', {name: 'Loading diagram'})).toBeVisible();
	});

	it('should render the empty message', async () => {
		const screen = await render(
			<DiagramShell status="empty" emptyMessage={{message: 'Nothing here', additionalInfo: 'Select something'}}>
				{null}
			</DiagramShell>,
		);

		await expect.element(screen.getByText('Nothing here')).toBeVisible();
		await expect.element(screen.getByText('Select something')).toBeVisible();
	});

	it('should render the error message', async () => {
		const screen = await render(<DiagramShell status="error">{null}</DiagramShell>);

		await expect.element(screen.getByText("Couldn't fetch data")).toBeVisible();
	});

	it('should render the forbidden message', async () => {
		const screen = await render(<DiagramShell status="forbidden">{null}</DiagramShell>);

		await expect.element(screen.getByText('Missing permissions to view the Definition')).toBeVisible();
	});
});
