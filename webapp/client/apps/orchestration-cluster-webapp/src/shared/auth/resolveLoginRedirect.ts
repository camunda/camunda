/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {RegisteredRouter} from '@tanstack/react-router';
import {z} from 'zod';

type FileRouteTypes = RegisteredRouter['routeTree']['types']['fileRouteTypes'];

const TASKLIST_LOGIN = {home: '/tasklist', login: '/tasklist/login'} as const;
const ADMIN_LOGIN = {home: '/admin', login: '/admin/login'} as const;

const APP_LOGINS = [ADMIN_LOGIN, TASKLIST_LOGIN] as const satisfies ReadonlyArray<{
	home: FileRouteTypes['to'];
	login: FileRouteTypes['to'];
}>;

type AppLogin = (typeof APP_LOGINS)[number];

type LoginRedirect = {
	to: AppLogin['login'];
	search: {redirect?: string};
};

// A dummy origin lets `URL` resolve `..` segments and backslash separators the same way a
// browser would before comparing, so a value like `/admin/../tasklist` cannot pass this check
// by looking like an admin path and then land outside it once the router normalizes it. Any
// input that is not a plain root-relative path -- an absolute URL, a protocol-relative one, or one
// with backslashes or control characters that `URL` would silently rewrite -- is rejected before
// resolution, since it was never a path within this app to begin with.
const REDIRECT_RESOLUTION_ORIGIN = 'https://redirect.invalid';

const RAW_PATH = /^\/(?![\\/])[^\\\t\r\n]*$/;

function isPathWithin(path: string, base: string) {
	if (!RAW_PATH.test(path)) {
		return false;
	}

	let url: URL;

	try {
		url = new URL(path, REDIRECT_RESOLUTION_ORIGIN);
	} catch {
		return false;
	}

	return url.origin === REDIRECT_RESOLUTION_ORIGIN && (url.pathname === base || url.pathname.startsWith(`${base}/`));
}

function resolveAppLoginRedirect({pathname, href}: {pathname: string; href: string}): LoginRedirect | undefined {
	const app = APP_LOGINS.find(({home}) => isPathWithin(pathname, home));

	if (app === undefined) {
		return undefined;
	}

	return {
		to: app.login,
		search: href === app.home ? {} : {redirect: href},
	};
}

function resolveLoginRedirect(location: {pathname: string; href: string}): LoginRedirect {
	const redirect = resolveAppLoginRedirect(location);

	if (redirect === undefined) {
		// Outside any known app — the root included. Each login page only accepts a `redirect` into its own
		// app, so passing this href on would fail the login route's own search validation and render the
		// generic error page instead of a form. Tasklist is where /_shadcn/_auth/ sends the root anyway.
		return {to: TASKLIST_LOGIN.login, search: {}};
	}

	return redirect;
}

/**
 * Search schema for an app's login route.
 *
 * `redirect` decides where the user is sent once authenticated, so an unconstrained value is an open
 * redirect. Confining it to the app's own paths is the rule that prevents that, and it lives here
 * rather than in each login route so the apps cannot end up enforcing different versions of it.
 * Excluding the login path itself keeps a signed-in user from being bounced back onto the form.
 */
function appLoginSearchSchema({home, login}: AppLogin) {
	return z.object({
		redirect: z
			.string()
			.refine(
				(value) => isPathWithin(value, home) && !isPathWithin(value, login),
				`Redirect must be a path within ${home}`,
			)
			.optional(),
	});
}

export {ADMIN_LOGIN, appLoginSearchSchema, resolveAppLoginRedirect, resolveLoginRedirect, TASKLIST_LOGIN};
