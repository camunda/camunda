/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {defineConfig} from 'vite';
import dts from 'vite-plugin-dts';

export default defineConfig({
	plugins: [dts({insertTypesEntry: true})],
	build: {
		lib: {
			entry: {index: 'lib/index.ts'},
			formats: ['es'],
		},
		minify: false,
		rollupOptions: {
			preserveEntrySignatures: 'strict',
			output: {preserveModules: true},
		},
	},
});
