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
				'8.8/index': resolve(__dirname, 'lib/8.8/index.ts'),
				'8.8/ad-hoc-sub-process': resolve(__dirname, 'lib/8.8/ad-hoc-sub-process.ts'),
				'8.8/authentication': resolve(__dirname, 'lib/8.8/authentication.ts'),
				'8.8/authorization': resolve(__dirname, 'lib/8.8/authorization.ts'),
				'8.8/batch-operation': resolve(__dirname, 'lib/8.8/batch-operation.ts'),
				'8.8/clock': resolve(__dirname, 'lib/8.8/clock.ts'),
				'8.8/cluster': resolve(__dirname, 'lib/8.8/cluster.ts'),
				'8.8/decision-definition': resolve(__dirname, 'lib/8.8/decision-definition.ts'),
				'8.8/decision-instance': resolve(__dirname, 'lib/8.8/decision-instance.ts'),
				'8.8/decision-requirements': resolve(__dirname, 'lib/8.8/decision-requirements.ts'),
				'8.8/document': resolve(__dirname, 'lib/8.8/document.ts'),
				'8.8/element-instance': resolve(__dirname, 'lib/8.8/element-instance.ts'),
				'8.8/group': resolve(__dirname, 'lib/8.8/group.ts'),
				'8.8/incident': resolve(__dirname, 'lib/8.8/incident.ts'),
				'8.8/job': resolve(__dirname, 'lib/8.8/job.ts'),
				'8.8/license': resolve(__dirname, 'lib/8.8/license.ts'),
				'8.8/mapping-rule': resolve(__dirname, 'lib/8.8/mapping-rule.ts'),
				'8.8/message': resolve(__dirname, 'lib/8.8/message.ts'),
				'8.8/message-subscriptions': resolve(__dirname, 'lib/8.8/message-subscriptions.ts'),
				'8.8/process-definition': resolve(__dirname, 'lib/8.8/process-definition.ts'),
				'8.8/process-instance': resolve(__dirname, 'lib/8.8/process-instance.ts'),
				'8.8/resource': resolve(__dirname, 'lib/8.8/resource.ts'),
				'8.8/role': resolve(__dirname, 'lib/8.8/role.ts'),
				'8.8/signal': resolve(__dirname, 'lib/8.8/signal.ts'),
				'8.8/tenant': resolve(__dirname, 'lib/8.8/tenant.ts'),
				'8.8/usage-metrics': resolve(__dirname, 'lib/8.8/usage-metrics.ts'),
				'8.8/user': resolve(__dirname, 'lib/8.8/user.ts'),
				'8.8/user-task': resolve(__dirname, 'lib/8.8/user-task.ts'),
				'8.8/variable': resolve(__dirname, 'lib/8.8/variable.ts'),
				'8.9': resolve(__dirname, 'lib/8.9/index.ts'),
				'8.9/index': resolve(__dirname, 'lib/8.9/index.ts'),
				'8.9/audit-log': resolve(__dirname, 'lib/8.9/audit-log.ts'),
				'8.9/ad-hoc-sub-process': resolve(__dirname, 'lib/8.9/ad-hoc-sub-process.ts'),
				'8.9/authentication': resolve(__dirname, 'lib/8.9/authentication.ts'),
				'8.9/authorization': resolve(__dirname, 'lib/8.9/authorization.ts'),
				'8.9/batch-operation': resolve(__dirname, 'lib/8.9/batch-operation.ts'),
				'8.9/clock': resolve(__dirname, 'lib/8.9/clock.ts'),
				'8.9/cluster': resolve(__dirname, 'lib/8.9/cluster.ts'),
				'8.9/decision-definition': resolve(__dirname, 'lib/8.9/decision-definition.ts'),
				'8.9/decision-instance': resolve(__dirname, 'lib/8.9/decision-instance.ts'),
				'8.9/decision-requirements': resolve(__dirname, 'lib/8.9/decision-requirements.ts'),
				'8.9/document': resolve(__dirname, 'lib/8.9/document.ts'),
				'8.9/element-instance': resolve(__dirname, 'lib/8.9/element-instance.ts'),
				'8.9/group': resolve(__dirname, 'lib/8.9/group.ts'),
				'8.9/incident': resolve(__dirname, 'lib/8.9/incident.ts'),
				'8.9/job': resolve(__dirname, 'lib/8.9/job.ts'),
				'8.9/license': resolve(__dirname, 'lib/8.9/license.ts'),
				'8.9/mapping-rule': resolve(__dirname, 'lib/8.9/mapping-rule.ts'),
				'8.9/message': resolve(__dirname, 'lib/8.9/message.ts'),
				'8.9/message-subscriptions': resolve(__dirname, 'lib/8.9/message-subscriptions.ts'),
				'8.9/process-definition': resolve(__dirname, 'lib/8.9/process-definition.ts'),
				'8.9/user-task': resolve(__dirname, 'lib/8.9/user-task.ts'),
				'8.9/process-instance': resolve(__dirname, 'lib/8.9/process-instance.ts'),
				'8.9/resource': resolve(__dirname, 'lib/8.9/resource.ts'),
				'8.9/role': resolve(__dirname, 'lib/8.9/role.ts'),
				'8.9/signal': resolve(__dirname, 'lib/8.9/signal.ts'),
				'8.9/tenant': resolve(__dirname, 'lib/8.9/tenant.ts'),
				'8.9/usage-metrics': resolve(__dirname, 'lib/8.9/usage-metrics.ts'),
				'8.9/user': resolve(__dirname, 'lib/8.9/user.ts'),
				'8.9/variable': resolve(__dirname, 'lib/8.9/variable.ts'),
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
