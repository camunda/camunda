/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {themeStore} from './theme';
import {C3ThemePersister} from './C3ThemePersister';

const mockProfile = vi.hoisted(() => ({theme: 'light'}));

vi.mock('@camunda/camunda-composite-components', () => ({
	useC3Profile: () => mockProfile,
}));

describe('C3ThemePersister', () => {
	afterEach(() => {
		themeStore.reset();
		localStorage.clear();
	});

	it('should follow the profile theme without storing it when none is stored', async () => {
		mockProfile.theme = 'dark';

		await render(<C3ThemePersister />);

		expect(themeStore.selectedTheme).toBe('dark');
		expect(getStateLocally('theme')).toBeNull();
	});

	it('should keep the stored theme over the profile theme', async () => {
		themeStore.changeTheme('dark');
		mockProfile.theme = 'light';

		await render(<C3ThemePersister />);

		expect(themeStore.selectedTheme).toBe('dark');
		expect(getStateLocally('theme')).toBe('dark');
	});
});
