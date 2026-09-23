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

function renderInContainer(children: React.ReactElement, width: number) {
	return render(<TooltipProvider>{<div style={{width: `${width}px`}}>{children}</div>}</TooltipProvider>);
}

describe('<InstancesBar />', () => {
	it('should render the incidents count and turn it error-colored when there are incidents', async () => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={3} size="medium" />);

		const incidentsBadge = screen.getByTestId('incident-instances-badge');
		await expect.element(incidentsBadge).toHaveTextContent('3');
		await expect.element(incidentsBadge).toHaveClass('text-danger-foreground-strong');
	});

	it('should not color the incidents count when there are no incidents', async () => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={0} size="medium" />);

		const incidentsBadge = screen.getByTestId('incident-instances-badge');
		await expect.element(incidentsBadge).toHaveTextContent('0');
		await expect.element(incidentsBadge).not.toHaveClass('text-danger-foreground-strong');
	});

	it('should render the active instances count when it is defined', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={5} size="medium" />,
		);

		await expect.element(screen.getByTestId('active-instances-badge')).toHaveTextContent('5');
	});

	it('should hide the active instances count when it is undefined', async () => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={2} size="medium" />);

		expect(screen.getByTestId('active-instances-badge').elements()).toHaveLength(0);
	});

	it('should hide the active instances count when it is negative', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={-1} size="medium" />,
		);

		expect(screen.getByTestId('active-instances-badge').elements()).toHaveLength(0);
	});

	it('should apply the given className to the root element', async () => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={2} size="medium" className="foo" />);

		const root = screen.getByTestId('incident-instances-badge').element().parentElement?.parentElement;
		await expect.element(root as HTMLElement).toHaveClass('foo');
	});

	it('should color the label red only when the bar size is medium, regardless of the label size', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar
				incidentsCount={0}
				label={{type: 'incident', size: 'medium', text: 'order-process'}}
				size="medium"
			/>,
		);

		await expect.element(screen.getByText('order-process').first()).toHaveClass('text-danger-foreground-strong');
	});

	it('should not color an incident-type label red when the bar size is not medium', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar
				incidentsCount={0}
				label={{type: 'incident', size: 'medium', text: 'order-process'}}
				size="large"
			/>,
		);

		await expect.element(screen.getByText('order-process').first()).not.toHaveClass('text-danger-foreground-strong');
	});

	it('should use the small bar-height class for size="small"', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={8} size="small" />,
		);

		const bar = screen.getByTestId('instances-bar').element();
		await expect.element(bar.children[0] as HTMLElement).toHaveClass('h-0.5');
	});

	it('should use the medium bar-height class for size="medium"', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={8} size="medium" />,
		);

		const bar = screen.getByTestId('instances-bar').element();
		await expect.element(bar.children[0] as HTMLElement).toHaveClass('h-1');
	});

	it('should use the large bar-height class for size="large"', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={8} size="large" />,
		);

		const bar = screen.getByTestId('instances-bar').element();
		await expect.element(bar.children[0] as HTMLElement).toHaveClass('h-2');
	});

	it('should render a defined zero active instances count without the active color', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={0} size="medium" />,
		);

		const activeBadge = screen.getByTestId('active-instances-badge');
		await expect.element(activeBadge).toHaveTextContent('0');
		await expect.element(activeBadge).not.toHaveClass('text-success-foreground-strong');
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

		await userEvent.hover(screen.getByTestId('draining-indicator').element());

		await expect.element(screen.getByText('Scheduled for deletion')).toBeVisible();
	});

	it('should keep the draining indicator next to a short label even in a very wide row', async () => {
		const screen = await renderInContainer(
			<InstancesBar
				incidentsCount={0}
				label={{type: 'process', size: 'medium', text: 'short-process-name'}}
				isDraining
				drainingDescription="This instance is draining"
				size="medium"
			/>,
			1800,
		);

		const labelRect = screen.getByText('short-process-name').element().getBoundingClientRect();
		const drainingRect = screen.getByTestId('draining-indicator').element().getBoundingClientRect();

		expect(drainingRect.left - labelRect.right).toBeLessThan(20);
	});

	it('should ellipsize the label instead of pushing the draining indicator or active count out of view', async () => {
		const longText = 'a-very-long-process-definition-name-that-does-not-fit-in-a-narrow-row';
		const screen = await renderInContainer(
			<InstancesBar
				incidentsCount={0}
				activeInstancesCount={5}
				label={{type: 'process', size: 'medium', text: longText}}
				isDraining
				drainingDescription="This instance is draining"
				size="medium"
			/>,
			320,
		);

		const labelEl = screen.getByText(longText).element();
		expect(labelEl.scrollWidth).toBeGreaterThan(labelEl.clientWidth);
		await expect.element(screen.getByTestId('draining-indicator')).toBeVisible();
		await expect.element(screen.getByTestId('active-instances-badge')).toBeVisible();
	});

	it('should reveal the full label text in a tooltip on hover', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar
				incidentsCount={0}
				label={{type: 'process', size: 'medium', text: 'order-process'}}
				size="medium"
			/>,
		);

		await userEvent.hover(screen.getByText('order-process').first().element());

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

		expect(screen.getByTestId('draining-indicator').elements()).toHaveLength(0);
	});

	it('should not render the draining indicator when no description is provided', async () => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={0} isDraining size="medium" />);

		expect(screen.getByTestId('draining-indicator').elements()).toHaveLength(0);
	});

	it('should render the two-segment bar when activeInstancesCount is defined', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={8} size="medium" />,
		);

		await expect.element(screen.getByTestId('instances-bar')).toBeVisible();
	});

	it('should not render the bar when activeInstancesCount is undefined', async () => {
		const screen = await renderWithTooltipProvider(<InstancesBar incidentsCount={2} size="medium" />);

		expect(screen.getByTestId('instances-bar').elements()).toHaveLength(0);
	});

	it('should size the incidents segment to its share of the total', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={2} activeInstancesCount={8} size="medium" />,
		);

		const bar = screen.getByTestId('instances-bar').element();
		const incidentsSegment = bar.children[1] as HTMLElement;
		expect(incidentsSegment.style.width).toBe('20%');
	});

	it('should not divide by zero when both counts are zero', async () => {
		const screen = await renderWithTooltipProvider(
			<InstancesBar incidentsCount={0} activeInstancesCount={0} size="medium" />,
		);

		const bar = screen.getByTestId('instances-bar').element();
		const incidentsSegment = bar.children[1] as HTMLElement;
		expect(incidentsSegment.style.width).toBe('0%');
	});
});
