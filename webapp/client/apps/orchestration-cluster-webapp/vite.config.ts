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
			'#/modules': path.resolve(__dirname, './src/modules'),
			'#/assets': path.resolve(__dirname, './src/assets'),
			'#/pages': path.resolve(__dirname, './src/pages'),
		},
	},
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
		outputFile: process.env['CI'] ? {junit: 'TEST-unit.xml'} : undefined,
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
