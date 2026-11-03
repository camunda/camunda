/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TooltipProvider} from '@camunda/design-system';
import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {TruncatedText} from './TruncatedText';

const LONG_TEXT = 'a-very-long-variable-name-that-does-not-fit-in-its-column-and-gets-truncated';

describe('<TruncatedText />', () => {
	it('should reveal the full text in a tooltip on keyboard focus', async () => {
		const screen = await render(
			<TooltipProvider>
				<div className="w-24">
					<TruncatedText text={LONG_TEXT} />
				</div>
			</TooltipProvider>,
		);

		await expect.element(screen.getByRole('tooltip')).not.toBeInTheDocument();

		await userEvent.tab();

		await expect.element(screen.getByRole('tooltip')).toHaveTextContent(LONG_TEXT);
	});

	it('should reveal the full text in a tooltip on hover when the visible content is shortened', async () => {
		const screen = await render(
			<TooltipProvider>
				<TruncatedText text={LONG_TEXT}>a-very-long…truncated</TruncatedText>
			</TooltipProvider>,
		);

		await userEvent.hover(screen.getByText('a-very-long…truncated'));

		await expect.element(screen.getByRole('tooltip')).toHaveTextContent(LONG_TEXT);
	});
});
