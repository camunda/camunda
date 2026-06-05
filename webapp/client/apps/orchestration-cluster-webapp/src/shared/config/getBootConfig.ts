/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const nullableString = z
	.string()
	.optional()
	.default('')
	.transform((value) => (value.length > 0 ? value : null));

const BootConfigSchema = z.object({
	contextPath: z.string().optional().default('/'),
	baseName: z.string().optional().default(''),
	isEnterprise: z.stringbool().optional().default(false),
	organizationId: nullableString,
	clusterId: nullableString,
	mixpanelToken: nullableString,
	mixpanelApiHost: nullableString,
});

type BootConfig = z.infer<typeof BootConfigSchema>;

const DEFAULT_BOOT_CONFIG = BootConfigSchema.safeParse({}).data!;

const getBootConfig = (() => {
	let cachedConfig: BootConfig | undefined;

	return (): BootConfig => {
		if (cachedConfig !== undefined) {
			return cachedConfig;
		}

		if (typeof document === 'undefined') {
			return DEFAULT_BOOT_CONFIG;
		}

		const {success, data} = BootConfigSchema.safeParse(document.documentElement.dataset);

		cachedConfig = success ? data : DEFAULT_BOOT_CONFIG;
		return cachedConfig;
	};
})();

export {getBootConfig};
export type {BootConfig};
