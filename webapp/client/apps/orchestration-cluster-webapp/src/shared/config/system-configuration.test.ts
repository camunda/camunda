/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getSystemConfigurationResponseBodySchema} from '@camunda/camunda-api-zod-schemas/8.10';
import {expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

it('should provide an enabled wait-state fixture by default', () => {
	const config = getSystemConfigurationResponseBodySchema.parse(createSystemConfiguration());

	expect(config.deployment.isWaitStatesEnabled).toBe(true);
});

it.for([false, true])('should preserve explicit wait-state enablement of %s', (isWaitStatesEnabled) => {
	const config = getSystemConfigurationResponseBodySchema.parse(
		createSystemConfiguration({deployment: {isWaitStatesEnabled}}),
	);

	expect(config.deployment.isWaitStatesEnabled).toBe(isWaitStatesEnabled);
	expect(config.deployment.isMultiTenancyEnabled).toBe(false);
});

it('should preserve existing deployment fixture overrides', () => {
	const config = getSystemConfigurationResponseBodySchema.parse(
		createSystemConfiguration({deployment: {isMultiTenancyEnabled: true, maxRequestSize: 4194304}}),
	);

	expect(config.deployment).toEqual({
		isMultiTenancyEnabled: true,
		isWaitStatesEnabled: true,
		maxRequestSize: 4194304,
	});
});

it.for([undefined, null, 'false'])('should reject invalid wait-state enablement of %s without a fallback', (value) => {
	const config = createSystemConfiguration();

	const result = getSystemConfigurationResponseBodySchema.safeParse({
		...config,
		deployment: {...config.deployment, isWaitStatesEnabled: value},
	});

	expect(result.success).toBe(false);
});
