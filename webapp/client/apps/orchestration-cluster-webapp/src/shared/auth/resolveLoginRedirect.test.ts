/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {ADMIN_LOGIN, appLoginSearchSchema, resolveLoginRedirect, TASKLIST_LOGIN} from './resolveLoginRedirect';

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

describe('appLoginSearchSchema', () => {
	function accepts(app: typeof ADMIN_LOGIN | typeof TASKLIST_LOGIN, redirect: string) {
		return appLoginSearchSchema(app).safeParse({redirect}).success;
	}

	it('should accept paths within the app', () => {
		expect(accepts(ADMIN_LOGIN, '/admin')).toBe(true);
		expect(accepts(ADMIN_LOGIN, '/admin/users')).toBe(true);
		expect(accepts(ADMIN_LOGIN, '/admin?page=2')).toBe(true);
		expect(accepts(ADMIN_LOGIN, '/admin#section')).toBe(true);
	});

	it('should treat a missing redirect as valid, since each login page has a default', () => {
		expect(appLoginSearchSchema(ADMIN_LOGIN).safeParse({}).success).toBe(true);
	});

	it('should reject another app, so a login page cannot bounce the user elsewhere', () => {
		expect(accepts(ADMIN_LOGIN, '/tasklist')).toBe(false);
		expect(accepts(TASKLIST_LOGIN, '/admin/users')).toBe(false);
	});

	it('should reject absolute and protocol-relative URLs, which would be an open redirect', () => {
		expect(accepts(ADMIN_LOGIN, 'https://evil.example')).toBe(false);
		expect(accepts(ADMIN_LOGIN, '//evil.example')).toBe(false);
		expect(accepts(ADMIN_LOGIN, '/admin.evil.example')).toBe(false);
	});

	it('should reject a path that only escapes the app once its dot segments or backslashes are resolved', () => {
		expect(accepts(ADMIN_LOGIN, '/admin/../tasklist')).toBe(false);
		expect(accepts(ADMIN_LOGIN, '/admin\\..\\tasklist')).toBe(false);
	});

	it('should reject a lookalike prefix', () => {
		expect(accepts(ADMIN_LOGIN, '/administration')).toBe(false);
		expect(accepts(TASKLIST_LOGIN, '/tasklisting')).toBe(false);
	});

	it('should reject the login page itself, which would loop back onto the form', () => {
		expect(accepts(ADMIN_LOGIN, '/admin/login')).toBe(false);
		expect(accepts(ADMIN_LOGIN, '/admin/login?redirect=/admin')).toBe(false);
		expect(accepts(TASKLIST_LOGIN, '/tasklist/login')).toBe(false);
	});
});
