/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {parseAdminClientConfig} from './adminClientConfig';

describe('parseAdminClientConfig', () => {
	it('should parse the script served by the Admin config endpoint', () => {
		// given
		const script = `window.clientConfig = ${JSON.stringify({
			idPattern: '^[a-z]+$',
			resourcePermissions: {USER_TASK: ['READ', 'COMPLETE']},
			defaultRoleIds: ['admin'],
		})};`;

		// when
		const config = parseAdminClientConfig(script);

		// then
		expect(config).toEqual({
			idPattern: '^[a-z]+$',
			resourcePermissions: {USER_TASK: ['READ', 'COMPLETE']},
			defaultRoleIds: ['admin'],
		});
	});

	it('should apply defaults for missing fields and ignore unrelated ones', () => {
		// given
		const script = 'window.clientConfig = {"isEnterprise": true};\n';

		// when
		const config = parseAdminClientConfig(script);

		// then
		expect(config).toEqual({resourcePermissions: {}, defaultRoleIds: []});
	});

	it('should throw when the script is not valid JSON', () => {
		// given
		const script = 'window.clientConfig = {oops';

		// when
		const parse = () => parseAdminClientConfig(script);

		// then
		expect(parse).toThrow();
	});

	it('should throw when a field has an unexpected shape', () => {
		// given
		const script = 'window.clientConfig = {"defaultRoleIds": "admin"};';

		// when
		const parse = () => parseAdminClientConfig(script);

		// then
		expect(parse).toThrow();
	});
});
