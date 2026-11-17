/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
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
	return render(
		<div data-testid="metrics">
			<MessageMetrics metrics={metrics === null ? null : {...EMPTY_METRICS, ...metrics}} />
		</div>,
	);
}

describe('<MessageMetrics />', () => {
	it.for([null, {}])('should render nothing for %s metrics', async (metrics) => {
		const screen = await renderMetrics(metrics);

		await expect.element(screen.getByTestId('metrics')).toBeEmptyDOMElement();
	});

	it('should render the total tokens with a breakdown and the duration', async () => {
		const screen = await renderMetrics({
			inputTokens: 1000,
			outputTokens: 200,
			reasoningTokenCount: 50,
			cacheCreationTokenCount: 30,
			cacheReadTokenCount: 20,
			durationMs: 1234,
		});

		await expect
			.element(screen.getByTestId('metrics'))
			.toHaveTextContent('1,250 tokensInput: 1,000Cache read: 20Cache write: 30Output: 200 (50 reasoning)1.23s');
	});

	it('should show placeholders for missing token counts and omit missing cache lines', async () => {
		const screen = await renderMetrics({outputTokens: 0, durationMs: 0});

		await expect.element(screen.getByTestId('metrics')).toHaveTextContent('0 tokensInput: ---Output: 00ms');
	});

	it('should render only the duration when no token metrics exist', async () => {
		const screen = await renderMetrics({durationMs: 999});

		await expect.element(screen.getByTestId('metrics')).toHaveTextContent('999ms');
	});
});
