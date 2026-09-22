/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {resolveLoginRedirect} from './resolveLoginRedirect';

function at(href: string) {
	return {pathname: new URL(href, 'http://localhost').pathname, href};
}

describe('resolveLoginRedirect', () => {
	it('should send each app to its own login page', () => {
		expect(resolveLoginRedirect(at('/admin/users')).to).toBe('/admin/login');
		expect(resolveLoginRedirect(at('/tasklist/processes')).to).toBe('/tasklist/login');
	});

	it('should preserve the requested URL so login can return to it', () => {
		expect(resolveLoginRedirect(at('/admin/users?page=2'))).toEqual({
			to: '/admin/login',
			search: {redirect: '/admin/users?page=2'},
		});
	});

	it('should omit the redirect for an app index, which login already defaults to', () => {
		expect(resolveLoginRedirect(at('/admin')).search).toEqual({});
		expect(resolveLoginRedirect(at('/tasklist')).search).toEqual({});
	});

	it('should not mistake a lookalike prefix for an app', () => {
		expect(resolveLoginRedirect(at('/administration')).to).toBe('/tasklist/login');
	});

	it('should fall back to Tasklist outside any known app', () => {
		expect(resolveLoginRedirect(at('/')).to).toBe('/tasklist/login');
	});

	it('should omit the redirect outside any known app, which no login page would accept', () => {
		expect(resolveLoginRedirect(at('/')).search).toEqual({});
		expect(resolveLoginRedirect(at('/operate/processes')).search).toEqual({});
	});
});
