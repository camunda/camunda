/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {TooltipProvider} from '@camunda/design-system';
import {InstancesBar} from './InstancesBar';

// The component relies on an app-level `<TooltipProvider>` (rendered once in
// `Header.tsx`) rather than wrapping itself, to avoid nesting providers in
// real usage. A standalone render needs its own, local, top-level provider.
function renderWithTooltipProvider(children: React.ReactElement) {
	return render(<TooltipProvider>{children}</TooltipProvider>);
}

describe('<InstancesBar />', () => {
	it('renders the incidents count and turns it error-colored when there are incidents', async () => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={3} size="medium" />);

		const incidentsBadge = screen.getByTestId('incident-instances-badge');
		await expect.element(incidentsBadge).toHaveTextContent('3');
		await expect.element(incidentsBadge).toHaveClass('text-danger-foreground-strong');
	});

	it('does not color the incidents count when there are no incidents', async () => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={0} size="medium" />);

		const incidentsBadge = screen.getByTestId('incident-instances-badge');
		await expect.element(incidentsBadge).toHaveTextContent('0');
		await expect.element(incidentsBadge).not.toHaveClass('text-danger-foreground-strong');
	});

	it('renders the active instances count when it is defined', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={5} size="medium" />,
		);

		await expect.element(screen.getByTestId('active-instances-badge')).toHaveTextContent('5');
	});

	it('hides the active instances count when it is undefined', async () => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={2} size="medium" />);

		expect(screen.getByTestId('active-instances-badge').elements()).toHaveLength(0);
	});

	it('renders the draining indicator when draining and a description are provided', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={0} isDraining drainingDescription="This instance is draining" size="medium" />,
		);

		await expect.element(screen.getByTestId('draining-indicator')).toBeVisible();
	});

	it('does not render the draining indicator when isDraining is false', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar
				incidentsCount={0}
				isDraining={false}
				drainingDescription="This instance is draining"
				size="medium"
			/>,
		);

		expect(screen.getByTestId('draining-indicator').elements()).toHaveLength(0);
	});

	it('does not render the draining indicator when no description is provided', async () => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={0} isDraining size="medium" />);

		expect(screen.getByTestId('draining-indicator').elements()).toHaveLength(0);
	});

	it('renders the two-segment bar when activeInstancesCount is defined', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={8} size="medium" />,
		);

		await expect.element(screen.getByTestId('instances-bar')).toBeVisible();
	});

	it('does not render the bar when activeInstancesCount is undefined', async () => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={2} size="medium" />);

		expect(screen.getByTestId('instances-bar').elements()).toHaveLength(0);
	});
});
