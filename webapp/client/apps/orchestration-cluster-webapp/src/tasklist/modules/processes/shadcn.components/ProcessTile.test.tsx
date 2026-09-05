/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createProcessDefinition} from '#/shared-test-modules/api-mocks/process-definitions';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {ProcessTile} from './ProcessTile';

describe('<ProcessTile />', () => {
	it('should display the process name and process-definition ID', async () => {
		const screen = await render(
			<ProcessTile
				process={createProcessDefinition({name: 'Invoice review', processDefinitionId: 'invoice-review'})}
				status="inactive"
				isStartButtonDisabled={false}
				onStartProcess={vi.fn()}
			/>,
		);

		await expect.element(screen.getByRole('heading', {name: 'Invoice review'})).toBeVisible();
		await expect.element(screen.getByText('invoice-review')).toBeVisible();
	});

	it('should use the process-definition ID when the process has no name', async () => {
		const screen = await render(
			<ProcessTile
				process={createProcessDefinition({name: null, processDefinitionId: 'invoice-review'})}
				status="inactive"
				isStartButtonDisabled={false}
				onStartProcess={vi.fn()}
			/>,
		);

		await expect.element(screen.getByRole('heading', {name: 'invoice-review'})).toBeVisible();
		await expect.element(screen.getByText('invoice-review')).toHaveLength(1);
	});

	it('should display whether the process requires form input', async () => {
		const screen = await render(
			<ProcessTile
				process={createProcessDefinition({name: 'Invoice review', hasStartForm: true})}
				status="inactive"
				isStartButtonDisabled={false}
				onStartProcess={vi.fn()}
			/>,
		);

		await expect.element(screen.getByText('Requires form input')).toBeVisible();
	});

	it('should display the start-process button', async () => {
		const screen = await render(
			<ProcessTile
				process={createProcessDefinition()}
				status="inactive"
				isStartButtonDisabled={false}
				onStartProcess={vi.fn()}
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'Start process'})).toBeVisible();
	});

	it('should call onStartProcess when the start-process button is clicked', async () => {
		const onStartProcess = vi.fn();
		const screen = await render(
			<ProcessTile
				process={createProcessDefinition()}
				status="inactive"
				isStartButtonDisabled={false}
				onStartProcess={onStartProcess}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Start process'}));

		expect(onStartProcess).toHaveBeenCalledOnce();
	});

	it('should disable the start-process button', async () => {
		const screen = await render(
			<ProcessTile
				process={createProcessDefinition()}
				status="inactive"
				isStartButtonDisabled
				onStartProcess={vi.fn()}
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'Start process'})).toBeDisabled();
	});

	it('should display the starting-process status', async () => {
		const screen = await render(
			<ProcessTile
				process={createProcessDefinition()}
				status="active"
				isStartButtonDisabled
				onStartProcess={vi.fn()}
			/>,
		);

		await expect.element(screen.getByText('Starting process...')).toBeVisible();
	});

	it('should display the process-started status', async () => {
		const screen = await render(
			<ProcessTile
				process={createProcessDefinition()}
				status="finished"
				isStartButtonDisabled
				onStartProcess={vi.fn()}
			/>,
		);

		await expect.element(screen.getByText('Process started')).toBeVisible();
	});

	it('should display the process-start-failed status', async () => {
		const screen = await render(
			<ProcessTile process={createProcessDefinition()} status="error" isStartButtonDisabled onStartProcess={vi.fn()} />,
		);

		await expect.element(screen.getByText('Process start failed')).toBeVisible();
	});
});
