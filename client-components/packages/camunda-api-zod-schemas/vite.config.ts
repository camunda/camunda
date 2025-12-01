/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {resolve} from 'node:path';
import {defineConfig} from 'vite';
import dts from 'vite-plugin-dts';
import circleDependency from 'vite-plugin-circular-dependency';

export default defineConfig({
	plugins: [
		dts({
			insertTypesEntry: true,
		}),
		circleDependency(),
	],
	build: {
		lib: {
			entry: {
				'8.8': resolve(__dirname, 'lib/8.8/index.ts'),
				'8.9': resolve(__dirname, 'lib/8.9/index.ts'),
			},
			formats: ['es'],
		},
		minify: false,
		rollupOptions: {
			external: ['zod'],
			preserveEntrySignatures: 'strict',
			output: {
				preserveModules: true,
			},
		},
	},
});
