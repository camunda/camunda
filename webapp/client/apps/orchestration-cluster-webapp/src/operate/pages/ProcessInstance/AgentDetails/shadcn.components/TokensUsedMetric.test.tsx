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
import {TokensUsedMetric} from './TokensUsedMetric';

describe('<TokensUsedMetric />', () => {
	it('should render the total without reasoning tokens and hide empty breakdown rows', async () => {
		const screen = await render(
			<TokensUsedMetric
				inputTokens={4}
				outputTokens={2}
				cacheReadTokens={0}
				cacheCreationTokens={0}
				reasoningTokens={0}
				maxTokens={10}
			/>,
		);

		const card = screen.getByRole('article', {name: 'Tokens Used'});
		await expect.element(card).toMatchTextContent('Input4Output2');
		await expect.element(card.getByRole('meter')).toHaveAttribute('aria-valuetext', '6 of 10 limit (60%)');
		await expect.element(card.getByText('Reasoning')).not.toBeInTheDocument();
		await expect.element(card.getByText('Cache read')).not.toBeInTheDocument();
		await expect.element(card.getByText('Cache write')).not.toBeInTheDocument();
	});

	it('should render reasoning and both cache rows when any cache bucket is used', async () => {
		const screen = await render(
			<TokensUsedMetric
				inputTokens={4}
				outputTokens={2}
				cacheReadTokens={7}
				cacheCreationTokens={0}
				reasoningTokens={3}
				maxTokens={-1}
			/>,
		);

		const card = screen.getByRole('article', {name: 'Tokens Used'});
		await expect.element(card).toMatchTextContent('13');
		await expect.element(card).toMatchTextContent('Reasoning3');
		await expect.element(card).toMatchTextContent('Cache read7Cache write0');
		await expect.element(card.getByRole('meter')).not.toBeInTheDocument();
	});
});
