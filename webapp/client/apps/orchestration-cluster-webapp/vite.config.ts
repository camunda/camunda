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
import path from 'node:path';
import tailwindcss from '@tailwindcss/vite';

const injectCustomCss: PluginOption = {
	name: 'inject-custom-css',
	apply: 'build',
	transformIndexHtml: {
		order: 'post',
		handler: () => [
			{
				tag: 'link',
				attrs: {rel: 'stylesheet', href: './custom.css'},
				injectTo: 'head',
			},
		],
	},
};

const basePlugins: PluginOption[] = [
	devtools(),
	tailwindcss(),
	tanstackRouter({
		target: 'react',
		autoCodeSplitting: true,
	}),
	viteReact(),
	injectCustomCss,
];

const config = defineConfig(({mode}) => ({
	base: mode === 'production' ? './' : undefined,
	resolve: {
		tsconfigPaths: true,
		// remove the explicit alias config when this is fixed: https://github.com/vitejs/vite/issues/21889
		alias: {
			'#/shared': path.resolve(import.meta.dirname, './src/shared'),
			'#/operate': path.resolve(import.meta.dirname, './src/operate'),
			'#/tasklist': path.resolve(import.meta.dirname, './src/tasklist'),
			'#/admin': path.resolve(import.meta.dirname, './src/admin'),
		},
	},
	plugins: mode === 'sbom' ? [...basePlugins, sbom({specVersion: '1.6'})] : basePlugins,
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
			'/session/heartbeat': {
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
				// Tanstack Router codesplitting was breaking without this flag
				strictExecutionOrder: true,
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
		include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
		reporters: process.env['CI'] ? ['default', 'github-actions', 'html', 'junit'] : ['default'],
		outputFile: process.env['CI']
			? {
					html: 'test-artifacts/html/index.html',
					junit: 'TEST-unit.xml',
				}
			: undefined,
		retry: process.env['CI'] ? 3 : 0,
		browser: {
			enabled: true,
			screenshotFailures: Boolean(process.env['CI']),
			screenshotDirectory: 'test-artifacts/screenshots',
			headless: true,
			viewport: {
				width: 1280,
				height: 720,
			},
			provider: playwright(),
			instances: [
				{
					browser: 'chromium',
					name: 'carbon',
					include: [
						'src/admin/**/*.test.ts',
						'src/admin/**/*.test.tsx',
						'src/operate/**/*.test.ts',
						'src/operate/**/*.test.tsx',
						'src/routes/_carbon/**/*.test.ts',
						'src/routes/_carbon/**/*.test.tsx',
						'src/shared/**/*.test.ts',
						'src/shared/**/*.test.tsx',
						'src/tasklist/**/*.test.ts',
						'src/tasklist/**/*.test.tsx',
						'src/vitest-modules/**/*.test.ts',
						'src/vitest-modules/**/*.test.tsx',
					],
					exclude: ['src/**/shadcn.components/**/*.test.ts', 'src/**/shadcn.components/**/*.test.tsx'],
					setupFiles: ['./src/vitest-modules/vitest-carbon.setup.ts'],
				},
				{
					browser: 'chromium',
					name: 'shadcn',
					include: [
						'src/**/shadcn.components/**/*.test.ts',
						'src/**/shadcn.components/**/*.test.tsx',
						'src/routes/shadcn/**/*.test.ts',
						'src/routes/shadcn/**/*.test.tsx',
					],
					setupFiles: ['./src/vitest-modules/vitest-shadcn.setup.ts'],
				},
			],
		},
	},
}));

export default config;
