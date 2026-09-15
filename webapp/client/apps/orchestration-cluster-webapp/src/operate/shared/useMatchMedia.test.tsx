/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {isWidthBelowBreakpoint, useMatchMedia} from './useMatchMedia';

it('should update the match when the media query changes', async () => {
	function Preview() {
		const [query, setQuery] = useState('all');
		const matches = useMatchMedia(query);
		return <button onClick={() => setQuery('not all')}>{matches ? 'Matches' : 'No match'}</button>;
	}

	const screen = await render(<Preview />);
	await expect.element(screen.getByRole('button', {name: 'Matches'})).toBeVisible();
	await userEvent.click(screen.getByRole('button', {name: 'Matches'}));
	await expect.element(screen.getByRole('button', {name: 'No match'})).toBeVisible();
	expect(isWidthBelowBreakpoint('xlg')).toBe('(max-width: 82rem)');
});
