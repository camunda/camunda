/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import type {AgentInstanceHistoryItemMetrics} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {MessageMetrics} from './MessageMetrics';

const EMPTY_METRICS: AgentInstanceHistoryItemMetrics = {
	inputTokens: null,
	outputTokens: null,
	reasoningTokenCount: null,
	cacheCreationTokenCount: null,
	cacheReadTokenCount: null,
	durationMs: null,
};

function renderMetrics(metrics: Partial<AgentInstanceHistoryItemMetrics> | null) {
	return render(<MessageMetrics metrics={metrics === null ? null : {...EMPTY_METRICS, ...metrics}} />);
}

describe('<MessageMetrics />', () => {
	it.for([null, {}])('should render nothing for %s metrics', async (metrics) => {
		const screen = await renderMetrics(metrics);

		await expect.element(screen.getByRole('button', {name: /tokens/})).not.toBeInTheDocument();
	});

	it('should show the token breakdown tooltip when the token badge trigger is focused', async () => {
		const screen = await renderMetrics({
			inputTokens: 1000,
			outputTokens: 200,
			reasoningTokenCount: 50,
			cacheCreationTokenCount: 30,
			cacheReadTokenCount: 20,
		});

		await userEvent.tab();

		await expect.element(screen.getByRole('button', {name: '1,250 tokens'})).toHaveFocus();
		const tooltip = screen.getByRole('tooltip');
		await expect.element(tooltip).toBeVisible();
		await expect
			.element(tooltip)
			.toHaveTextContent('Input: 1,000Cache read: 20Cache write: 30Output: 200 (50 reasoning)');
	});

	it('should show placeholders for missing token counts and omit missing cache lines', async () => {
		const screen = await renderMetrics({outputTokens: 0, durationMs: 0});

		await expect.element(screen.getByRole('button', {name: '0 tokens'})).toBeVisible();
		await userEvent.hover(screen.getByRole('button', {name: '0 tokens'}));
		await expect.element(screen.getByRole('tooltip')).toHaveTextContent('Input: ---Output: 0');
		await expect.element(screen.getByText('0ms')).toBeVisible();
	});

	it('should render only the duration when no token metrics exist', async () => {
		const screen = await renderMetrics({durationMs: 999});

		await expect.element(screen.getByRole('button', {name: /tokens/})).not.toBeInTheDocument();
		await expect.element(screen.getByText('999ms')).toBeVisible();
	});
});
