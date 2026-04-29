/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/// <reference types="vitest" />
/// <reference types="vite/client" />

import {defineConfig, type PluginOption} from 'vite';
import {devtools} from '@tanstack/devtools-vite';
import {tanstackRouter} from '@tanstack/router-plugin/vite';
import viteReact from '@vitejs/plugin-react';
import sbom from 'rollup-plugin-sbom';
import {playwright} from '@vitest/browser-playwright';

const basePlugins: PluginOption[] = [
	devtools(),
	tanstackRouter({
		target: 'react',
		autoCodeSplitting: true,
	}),
	viteReact(),
];

const config = defineConfig(({mode}) => ({
	base: mode === 'production' ? './' : undefined,
	resolve: {tsconfigPaths: true},
	plugins: mode === 'sbom' ? [...basePlugins, sbom()] : basePlugins,
	server: {
		port: 3000,
		open: true,
		proxy: {
			'/v2': 'http://localhost:8080',
			'/login': {
				target: 'http://localhost:8080',
				bypass: (req) => (req.method !== 'POST' ? '/' : undefined),
			},
			'/logout': {
				target: 'http://localhost:8080',
				bypass: (req) => (req.method !== 'POST' ? '/' : undefined),
			},
		},
	},
	build: {
		sourcemap: mode !== 'sbom',
		license: {
			fileName: 'assets/vendor.LICENSE.txt',
		},
		rolldownOptions: {
			output: {
				postBanner: '/*! licenses: /assets/vendor.LICENSE.txt */',
			},
			input: {
				index: mode === 'visual-regression' ? './index.html' : './index.prod.html',
			},
		},
	},
	preview: {
		port: 3003,
		open: false,
		proxy: {},
	},
	test: {
		globals: true,
		include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
		browser: {
			enabled: true,
			screenshotFailures: false,
			headless: true,
			provider: playwright(),
			instances: [
				{
					browser: 'chromium',
					setupFiles: ['./src/vitest-modules/vitest.setup.ts'],
				},
			],
		},
	},
}));

export default config;
