/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {Toaster} from '@camunda/design-system';
import {it} from '#/vitest-modules/test-extend';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
	<>
		<Toaster />
		{children}
	</>
);

describe('<NetworkStatusWatcher />', () => {
	it('should display a persistent notification when initially offline', async () => {
		vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(false);

		const screen = await render(<NetworkStatusWatcher />, {wrapper: Wrapper});

		await expect.element(screen.getByText('Internet connection lost')).toBeVisible();
	});

	it('should display one notification while offline and remove it after reconnecting', async () => {
		vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(true);
		const screen = await render(<NetworkStatusWatcher />, {wrapper: Wrapper});

		expect(screen.getByText('Internet connection lost').elements()).toHaveLength(0);

		window.dispatchEvent(new Event('offline'));
		window.dispatchEvent(new Event('offline'));

		await expect.element(screen.getByText('Internet connection lost')).toBeVisible();
		expect(screen.getByText('Internet connection lost').elements()).toHaveLength(1);

		window.dispatchEvent(new Event('online'));

		await expect.element(screen.getByText('Internet connection lost')).not.toBeInTheDocument();
	});
});
