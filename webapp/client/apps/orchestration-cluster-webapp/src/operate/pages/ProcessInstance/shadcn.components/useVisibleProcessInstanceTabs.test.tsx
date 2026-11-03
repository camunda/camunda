/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {cleanup} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {useVisibleProcessInstanceTabs} from './useVisibleProcessInstanceTabs';

const path = '/operate-preview/processes/$processInstanceId/history';
const initialEntry = '/operate-preview/processes/42/history';

function DesktopTabs() {
	const {visibleTabs} = useVisibleProcessInstanceTabs({
		processInstanceId: '42',
		hasSelection: false,
		isDesktop: true,
		isProcessLevelWaiting: false,
		isWaitStateLoading: false,
	});
	return <output>{visibleTabs.join(',')}</output>;
}

function MobileTabs() {
	const {visibleTabs} = useVisibleProcessInstanceTabs({
		processInstanceId: '42',
		hasSelection: false,
		isDesktop: false,
		isProcessLevelWaiting: false,
		isWaitStateLoading: false,
	});
	return <output>{visibleTabs.join(',')}</output>;
}

describe('useVisibleProcessInstanceTabs', () => {
	afterEach(async () => {
		await cleanup();
	});

	it('should redirect the history tab to the variables tab on desktop', async () => {
		const screen = await renderWithRouter(DesktopTabs, {path, initialEntry});

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate-preview/processes/42/variables');
	});

	it('should keep the history tab on mobile', async () => {
		const screen = await renderWithRouter(MobileTabs, {path, initialEntry});

		await expect
			.element(screen.getByRole('status'))
			.toHaveTextContent('history,variables,incidents,listeners,operations-log,details');
		expect(screen.router.state.location.pathname).toBe('/operate-preview/processes/42/history');
	});
});
