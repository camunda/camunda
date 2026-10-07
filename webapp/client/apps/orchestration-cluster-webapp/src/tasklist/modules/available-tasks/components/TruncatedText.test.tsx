/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TooltipProvider} from '@camunda/design-system';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {TruncatedText} from './TruncatedText';

const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => <TooltipProvider>{children}</TooltipProvider>;

const LONG_TEXT = 'a-very-long-unbroken-text-that-cannot-possibly-fit-in-a-narrow-container';

describe('<TruncatedText />', () => {
	it('should reveal the full text in a tooltip on hover when it is truncated', async () => {
		const screen = await render(
			<div style={{width: 80}}>
				<TruncatedText>{LONG_TEXT}</TruncatedText>
			</div>,
			{wrapper: Wrapper},
		);

		await userEvent.hover(screen.getByText(LONG_TEXT));

		await expect.element(screen.getByRole('tooltip')).toHaveTextContent(LONG_TEXT);
	});

	it('should not show a tooltip on hover when the text fits', async () => {
		const screen = await render(
			<div style={{width: 400}}>
				<TruncatedText>Short text</TruncatedText>
			</div>,
			{wrapper: Wrapper},
		);

		await userEvent.hover(screen.getByText('Short text'));

		await expect.element(screen.getByRole('tooltip')).not.toBeInTheDocument();
	});
});
