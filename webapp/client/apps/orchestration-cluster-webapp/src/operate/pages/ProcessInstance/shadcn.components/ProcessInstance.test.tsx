/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {ProcessInstance} from './ProcessInstance';

const ProcessInstanceWithHeight: React.FC = () => (
	<div style={{height: '800px'}} className="flex flex-col">
		<ProcessInstance activeTab="variables" />
	</div>
);

describe('<ProcessInstance activeTab="variables" /> (DS scaffold)', () => {
	it('should render the sr-only process instance title', async () => {
		const screen = await renderWithRouter(ProcessInstanceWithHeight, {
			path: '/operate-preview/processes/$processInstanceId',
			initialEntry: '/operate-preview/processes/2251799813685249',
		});

		await expect.element(screen.getByRole('heading', {name: 'Operate Process Instance'})).toBeInTheDocument();
	});

	it('should render the header, diagram, and bottom panel placeholders', async () => {
		const screen = await renderWithRouter(ProcessInstanceWithHeight, {
			path: '/operate-preview/processes/$processInstanceId',
			initialEntry: '/operate-preview/processes/2251799813685249',
		});

		await expect.element(screen.getByText('Header placeholder')).toBeVisible();
		await expect.element(screen.getByText('Diagram panel placeholder')).toBeVisible();
		await expect.element(screen.getByText('Bottom panel placeholder')).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Variables'})).toBeVisible();
	});

	it('should mark the active tab and report tab changes', async () => {
		const onTabChange = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<div style={{height: '800px'}} className="flex flex-col">
					<ProcessInstance activeTab="incidents" onTabChange={onTabChange} />
				</div>
			),
			{
				path: '/operate-preview/processes/$processInstanceId',
				initialEntry: '/operate-preview/processes/2251799813685249',
			},
		);

		await expect.element(screen.getByRole('tab', {name: 'Incidents'})).toHaveAttribute('aria-selected', 'true');

		await screen.getByRole('tab', {name: 'Details'}).click();

		expect(onTabChange).toHaveBeenCalledWith('details');
	});
});
