/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {isAnonymousCurrentUser} from './currentUser';

describe('isAnonymousCurrentUser', () => {
	it('should detect an anonymous current user', async () => {
		expect(await isAnonymousCurrentUser(Response.json({anonymous: true, username: null}))).toBe(true);
	});

	it('should not treat an authenticated current user as anonymous', async () => {
		expect(await isAnonymousCurrentUser(Response.json({anonymous: false, username: 'demo'}))).toBe(false);
	});

	it('should not treat a current user without the flag as anonymous', async () => {
		expect(await isAnonymousCurrentUser(Response.json({username: 'demo'}))).toBe(false);
	});

	it('should not treat a body that is not JSON as anonymous', async () => {
		expect(await isAnonymousCurrentUser(new Response('not json'))).toBe(false);
	});
});
