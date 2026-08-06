/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createProcessDefinition} from '#/shared-test-modules/api-mocks/process-definitions';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {ProcessTile} from './ProcessTile';

describe('<ProcessTile />', () => {
	it('should display the process name and process-definition ID', async () => {
		const screen = await render(
			<ProcessTile
				process={createProcessDefinition({name: 'Invoice review', processDefinitionId: 'invoice-review'})}
			/>,
		);

		await expect.element(screen.getByRole('heading', {name: 'Invoice review'})).toBeVisible();
		await expect.element(screen.getByText('invoice-review')).toBeVisible();
	});

	it('should use the process-definition ID when the process has no name', async () => {
		const screen = await render(
			<ProcessTile process={createProcessDefinition({name: null, processDefinitionId: 'invoice-review'})} />,
		);

		await expect.element(screen.getByRole('heading', {name: 'invoice-review'})).toBeVisible();
		await expect.element(screen.getByText('invoice-review')).toHaveLength(1);
	});

	it('should display whether the process requires form input', async () => {
		const screen = await render(
			<ProcessTile process={createProcessDefinition({name: 'Invoice review', hasStartForm: true})} />,
		);

		await expect.element(screen.getByText('Requires form input')).toBeVisible();
	});

	it('should display the start-process button', async () => {
		const screen = await render(<ProcessTile process={createProcessDefinition()} />);

		await expect.element(screen.getByRole('button', {name: 'Start process'})).toBeVisible();
	});
});
