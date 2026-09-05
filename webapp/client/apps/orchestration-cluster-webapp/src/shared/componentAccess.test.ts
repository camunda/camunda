/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, it} from 'vitest';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {ComponentAccessDeniedError, ComponentNotAvailableError} from '#/shared/errors';
import {assertComponentAccessible, hasComponentAccess} from './componentAccess';

describe('component access', () => {
	afterEach(() => {
		sessionStorage.clear();
	});

	it('should allow component-specific and wildcard access', () => {
		expect(hasComponentAccess('tasklist', ['tasklist'])).toBe(true);
		expect(hasComponentAccess('tasklist', ['*'])).toBe(true);
	});

	it('should reject users without component access', () => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(createSystemConfiguration({components: {active: ['tasklist']}})),
		);

		expect(() => assertComponentAccessible('tasklist', [])).toThrow(ComponentAccessDeniedError);
	});

	it('should reject components that are not active', () => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));

		expect(() => assertComponentAccessible('tasklist', ['tasklist'])).toThrow(ComponentNotAvailableError);
	});
});
