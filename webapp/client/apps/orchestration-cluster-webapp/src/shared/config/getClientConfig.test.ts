/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getSystemConfigurationResponseBodySchema} from '@camunda/camunda-api-zod-schemas/8.10';
import {afterEach, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {clearSessionState, storeSessionState} from '#/shared/browser-storage/session-storage';
import {getClientConfig} from './getClientConfig';

afterEach(() => {
	clearSessionState('clientConfig');
});

it('should provide enabled wait states by default', () => {
	storeSessionState('clientConfig', createSystemConfiguration());

	expect(getClientConfig().deployment.waitStatesEnabled).toBe(true);
});

it.for([false, true])('should preserve explicit wait-state enablement of %s', (isWaitStatesEnabled) => {
	const config = createSystemConfiguration({deployment: {waitStatesEnabled: isWaitStatesEnabled}});
	storeSessionState('clientConfig', config);

	expect(getClientConfig()).toEqual(config);
	expect(getClientConfig().deployment.waitStatesEnabled).toBe(isWaitStatesEnabled);
});

it('should preserve existing deployment fixture overrides', () => {
	const config = getSystemConfigurationResponseBodySchema.parse(
		createSystemConfiguration({deployment: {isMultiTenancyEnabled: true, maxRequestSize: 4194304}}),
	);

	expect(config.deployment).toEqual({
		isMultiTenancyEnabled: true,
		waitStatesEnabled: true,
		maxRequestSize: 4194304,
	});
});

it.for([undefined, null, 'false', 0])(
	'should reject invalid wait-state enablement of %s without a fallback',
	(value) => {
		const config = createSystemConfiguration();
		const invalidConfig = {...config, deployment: {...config.deployment, waitStatesEnabled: value}};

		expect(getSystemConfigurationResponseBodySchema.safeParse(invalidConfig).success).toBe(false);
		sessionStorage.setItem('clientConfig', JSON.stringify(invalidConfig));
		expect(() => getClientConfig()).toThrow('Client config not initialized');
	},
);
