/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {getInitialTheme} from './theme';

const mockBootConfig = vi.hoisted(() => ({organizationId: null as string | null, clusterId: null as string | null}));

vi.mock('#/shared/config/getBootConfig', () => ({
	getBootConfig: () => mockBootConfig,
}));

describe('getInitialTheme', () => {
	beforeEach(() => {
		localStorage.clear();
	});

	afterEach(() => {
		mockBootConfig.organizationId = null;
		mockBootConfig.clusterId = null;
		localStorage.clear();
	});

	describe('on SaaS', () => {
		beforeEach(() => {
			mockBootConfig.organizationId = 'organization-id';
			mockBootConfig.clusterId = 'cluster-id';
		});

		it('should ignore a theme stored before explicit choices were tracked', () => {
			localStorage.setItem('theme', '"light"');

			expect(getInitialTheme()).toBe('system');
		});

		it('should use an explicit choice over a legacy theme', () => {
			localStorage.setItem('theme', '"light"');
			localStorage.setItem('themeChoice', '"dark"');

			expect(getInitialTheme()).toBe('dark');
		});
	});

	describe('on self-managed', () => {
		it('should keep a theme stored before explicit choices were tracked', () => {
			localStorage.setItem('theme', '"dark"');

			expect(getInitialTheme()).toBe('dark');
		});

		it('should use an explicit choice over a legacy theme', () => {
			localStorage.setItem('theme', '"dark"');
			localStorage.setItem('themeChoice', '"light"');

			expect(getInitialTheme()).toBe('light');
		});

		it('should default to the system theme when nothing is stored', () => {
			expect(getInitialTheme()).toBe('system');
		});
	});
});
