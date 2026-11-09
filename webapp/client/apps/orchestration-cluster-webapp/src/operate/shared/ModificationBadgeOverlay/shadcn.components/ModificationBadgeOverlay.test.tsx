/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import '#/shared/theme/tailwind.css';
import {afterEach, beforeEach, describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {ModificationBadgeOverlay} from './ModificationBadgeOverlay';

describe('<ModificationBadgeOverlay />', () => {
	const container = document.createElement('div');

	beforeEach(() => {
		document.body.appendChild(container);
	});

	afterEach(() => {
		container.remove();
	});

	it.for([
		{newTokenCount: 2, cancelledTokenCount: 0, name: '2 added'},
		{newTokenCount: 0, cancelledTokenCount: 3, name: '3 canceled'},
		{newTokenCount: 2, cancelledTokenCount: 3, name: '2 added, 3 canceled'},
	])('should name the badge "$name"', async ({newTokenCount, cancelledTokenCount, name}) => {
		const screen = await render(
			<ModificationBadgeOverlay
				container={container}
				newTokenCount={newTokenCount}
				cancelledTokenCount={cancelledTokenCount}
			/>,
		);

		await expect.element(screen.getByRole('img', {name, exact: true})).toBeVisible();
	});

	it('should render nothing without modifications', async () => {
		const screen = await render(
			<ModificationBadgeOverlay container={container} newTokenCount={0} cancelledTokenCount={0} />,
		);

		await expect.element(screen.getByRole('img')).not.toBeInTheDocument();
	});
});
