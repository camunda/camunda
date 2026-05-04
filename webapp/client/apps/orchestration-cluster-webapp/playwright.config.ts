/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {defineConfig, devices, type PlaywrightTestConfig} from '@playwright/test';
import playwrightPkg from '@playwright/test/package.json' with {type: 'json'};

const BASE_URL = 'http://localhost:3003';
const IS_CI = Boolean(process.env['CI']);
const USE_CONTAINERIZED_BROWSER = !IS_CI && Boolean(process.env['CONTAINERIZED_BROWSER']);
const FULL_HD_VIEWPORT = {width: 1920, height: 1080} as const;
const TABLET_VIEWPORT = {width: 1024, height: 768} as const;

const webServer: PlaywrightTestConfig['webServer'] = [
	{
		name: 'SPA Server',
		command: 'npx vite preview',
		stdout: 'pipe',
		stderr: 'pipe',
		url: BASE_URL,
	},
];

if (USE_CONTAINERIZED_BROWSER) {
	const version = playwrightPkg.version;
	webServer.push({
		name: 'Playwright Server',
		command: `docker run --rm --init --network host mcr.microsoft.com/playwright:v${version} /bin/sh -c "npx -y playwright@${version} run-server --host 0.0.0.0"`,
		stdout: 'pipe',
		stderr: 'pipe',
		wait: {
			stdout: /Listening on ws:\/\/0\.0\.0\.0:(?<PLAYWRIGHT_SERVER_PORT>\d+)\//,
		},
		timeout: 180_000, // 3min timeout in case the image has to be pulled first.
		gracefulShutdown: {signal: 'SIGTERM', timeout: 5000},
	});
}

export default defineConfig({
	testDir: './test',
	tsconfig: './tsconfig.node.json',
	fullyParallel: true,
	forbidOnly: IS_CI,
	retries: IS_CI ? 2 : 0,
	workers: IS_CI ? 1 : undefined,
	webServer,
	reporter: IS_CI
		? [
				['blob'],
				['github'],
				['html'],
				[
					'junit',
					{
						outputFile: 'results.xml',
					},
				],
			]
		: 'html',
	snapshotPathTemplate: '{testDir}/{testFileDir}/{testFileName}-snapshots/{arg}-{projectName}-linux{ext}',
	projects: [
		{
			name: 'visual-light',
			testMatch: 'visual/**/*.test.ts',
			use: {
				...devices['Desktop Chrome'],
				colorScheme: 'light',
				viewport: FULL_HD_VIEWPORT,
			},
		},
		{
			name: 'visual-dark',
			testMatch: 'visual/**/*.test.ts',
			use: {
				...devices['Desktop Chrome'],
				colorScheme: 'dark',
				viewport: FULL_HD_VIEWPORT,
			},
		},
		{
			name: 'visual-light-tablet',
			testMatch: 'visual/**/*.test.ts',
			use: {
				...devices['Desktop Chrome'],
				colorScheme: 'light',
				viewport: TABLET_VIEWPORT,
			},
		},
		{
			name: 'visual-dark-tablet',
			testMatch: 'visual/**/*.test.ts',
			use: {
				...devices['Desktop Chrome'],
				colorScheme: 'dark',
				viewport: TABLET_VIEWPORT,
			},
		},
		{
			name: 'a11y-light',
			testMatch: 'a11y/**/*.test.ts',
			use: {
				...devices['Desktop Chrome'],
				colorScheme: 'light',
				viewport: FULL_HD_VIEWPORT,
			},
		},
		{
			name: 'a11y-dark',
			testMatch: 'a11y/**/*.test.ts',
			use: {
				...devices['Desktop Chrome'],
				colorScheme: 'dark',
				viewport: FULL_HD_VIEWPORT,
			},
		},
		{
			name: 'integration',
			testMatch: 'integration/**/*.test.ts',
			use: {
				...devices['Desktop Chrome'],
				colorScheme: 'light',
				viewport: FULL_HD_VIEWPORT,
			},
		},
	],
	outputDir: 'test-results/',
	use: {
		viewport: FULL_HD_VIEWPORT,
		baseURL: BASE_URL,
		trace: 'retain-on-failure',
		screenshot: 'only-on-failure',
		video: 'retain-on-failure',
		...(USE_CONTAINERIZED_BROWSER && {
			connectOptions: {
				wsEndpoint: `ws://127.0.0.1:${process.env['PLAYWRIGHT_SERVER_PORT']}/`,
			},
		}),
	},
});
