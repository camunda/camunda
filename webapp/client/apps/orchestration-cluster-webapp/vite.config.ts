/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {defineConfig} from 'vite';
import {devtools} from '@tanstack/devtools-vite';
import {tanstackRouter} from '@tanstack/router-plugin/vite';
import viteReact from '@vitejs/plugin-react';

const config = defineConfig({
	resolve: {tsconfigPaths: true},
	plugins: [
		devtools(),
		tanstackRouter({
			target: 'react',
			autoCodeSplitting: true,
		}),
		viteReact(),
	],
});

export default config;
