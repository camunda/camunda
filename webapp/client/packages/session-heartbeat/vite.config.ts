/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/// <reference types="vitest" />

import {defineConfig} from 'vite';
import dts from 'vite-plugin-dts';
import {playwright} from '@vitest/browser-playwright';

export default defineConfig({
	plugins: [
		dts({
			insertTypesEntry: true,
			exclude: ['lib/**/*.test.ts', 'lib/**/*.test.tsx'],
		}),
	],
	build: {
		lib: {
			entry: {
				index: 'lib/index.ts',
				react: 'lib/react.ts',
			},
			formats: ['es'],
		},
		minify: false,
		rollupOptions: {
			external: ['react'],
			preserveEntrySignatures: 'strict',
			output: {
				preserveModules: true,
			},
		},
	},
	test: {
		include: ['lib/**/*.test.ts', 'lib/**/*.test.tsx'],
		reporters: process.env['CI'] ? ['default', 'github-actions', 'junit'] : ['default'],
		outputFile: process.env['CI'] ? {junit: 'TEST-unit.xml'} : undefined,
		browser: {
			enabled: true,
			screenshotFailures: false,
			headless: true,
			provider: playwright(),
			instances: [{browser: 'chromium'}],
		},
	},
});
