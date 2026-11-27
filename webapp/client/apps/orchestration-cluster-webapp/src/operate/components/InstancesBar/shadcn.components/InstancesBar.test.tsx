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
import {TooltipProvider} from '@camunda/design-system';
import {it} from '#/vitest-modules/test-extend';
import {InstancesBar} from './InstancesBar';

function renderWithTooltipProvider(children: React.ReactElement) {
	return render(<TooltipProvider>{children}</TooltipProvider>);
}

describe('<InstancesBar />', () => {
	it.for([3, 0])('should render an incidents count of %s', async (incidentsCount) => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={incidentsCount} size="medium" />);

		await expect.element(screen.getByTestId('incident-instances-badge')).toHaveTextContent(String(incidentsCount));
	});

	it('should render the active instances count when it is defined', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={5} size="medium" />,
		);

		await expect.element(screen.getByTestId('active-instances-badge')).toHaveTextContent('5');
	});

	it('should render neither the active instances count nor the bar when the count is undefined', async () => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={2} size="medium" />);

		await expect.element(screen.getByTestId('incident-instances-badge')).toBeVisible();
		await expect.element(screen.getByTestId('active-instances-badge')).not.toBeInTheDocument();
		await expect.element(screen.getByTestId('instances-bar')).not.toBeInTheDocument();
	});

	it('should hide the active instances count when it is negative', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={-1} size="medium" />,
		);

		await expect.element(screen.getByTestId('incident-instances-badge')).toBeVisible();
		await expect.element(screen.getByTestId('active-instances-badge')).not.toBeInTheDocument();
	});

	it('should render a defined zero active instances count', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={0} size="medium" />,
		);

		await expect.element(screen.getByTestId('active-instances-badge')).toHaveTextContent('0');
	});

	it('should render the draining indicator when draining and a description are provided', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={0} isDraining drainingDescription="This instance is draining" size="medium" />,
		);

		await expect.element(screen.getByTestId('draining-indicator')).toBeVisible();
	});

	it('should reveal the draining description tooltip on hover', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={0} isDraining drainingDescription="Scheduled for deletion" size="medium" />,
		);

		await userEvent.hover(screen.getByTestId('draining-indicator'));

		await expect.element(screen.getByText('Scheduled for deletion')).toBeVisible();
	});

	it('should reveal the full label text in a tooltip on hover', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar
				incidentsCount={0}
				label={{type: 'process', size: 'medium', text: 'order-process'}}
				size="medium"
				className="w-24"
			/>,
		);

		await userEvent.hover(screen.getByText('order-process').first());

		await expect.element(screen.getByText('order-process').nth(1)).toBeVisible();
	});

	it('should not render the draining indicator when isDraining is false', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar
				incidentsCount={0}
				isDraining={false}
				drainingDescription="This instance is draining"
				size="medium"
			/>,
		);

		await expect.element(screen.getByTestId('incident-instances-badge')).toBeVisible();
		await expect.element(screen.getByTestId('draining-indicator')).not.toBeInTheDocument();
	});

	it('should not render the draining indicator when no description is provided', async () => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={0} isDraining size="medium" />);

		await expect.element(screen.getByTestId('incident-instances-badge')).toBeVisible();
		await expect.element(screen.getByTestId('draining-indicator')).not.toBeInTheDocument();
	});

	it('should render the two-segment bar when activeInstancesCount is defined', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={8} size="medium" />,
		);

		await expect.element(screen.getByTestId('instances-bar')).toBeVisible();
	});
});
