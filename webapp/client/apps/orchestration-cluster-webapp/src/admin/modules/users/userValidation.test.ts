/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {isValidEmail, isValidUsername} from './userValidation';

describe('isValidUsername', () => {
	it.for(['jane.doe', 'jane_doe-99', 'a', '~@+.-_'])('should accept %s', (username) => {
		expect(isValidUsername(username)).toBe(true);
	});

	it.for(['jane doe', 'jane/doe', 'jane#doe', ''])('should reject %s', (username) => {
		expect(isValidUsername(username)).toBe(false);
	});
});

describe('isValidEmail', () => {
	it.for(['jane.doe@example.com', 'jane+doe@example.co.uk'])('should accept %s', (email) => {
		expect(isValidEmail(email)).toBe(true);
	});

	it.for(['jane.doe', 'jane.doe@', '@example.com', 'jane doe@example.com'])('should reject %s', (email) => {
		expect(isValidEmail(email)).toBe(false);
	});
});
