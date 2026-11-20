/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {endpoints} from './endpoints';

describe('user endpoints', () => {
	it.for([
		['getUser', endpoints.getUser],
		['updateUser', endpoints.updateUser],
		['deleteUser', endpoints.deleteUser],
	] as const)('should keep a username with reserved characters in the path of %s', ([, buildRequest]) => {
		// given
		const username = 'jane#other/1?x';

		// when
		const {pathname, hash, search} = new URL(buildRequest({username}).url);

		// then
		expect(pathname.endsWith('/users/jane%23other%2F1%3Fx')).toBe(true);
		expect(hash).toBe('');
		expect(search).toBe('');
	});
});
