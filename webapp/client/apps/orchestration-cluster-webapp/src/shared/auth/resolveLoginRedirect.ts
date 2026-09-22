/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {RegisteredRouter} from '@tanstack/react-router';

type FileRouteTypes = RegisteredRouter['routeTree']['types']['fileRouteTypes'];

const TASKLIST = {home: '/tasklist', login: '/tasklist/login'} as const;
const ADMIN = {home: '/admin', login: '/admin/login'} as const;

const APP_LOGINS = [ADMIN, TASKLIST] as const satisfies ReadonlyArray<{
	home: FileRouteTypes['to'];
	login: FileRouteTypes['to'];
}>;

type LoginRedirect = {
	to: (typeof APP_LOGINS)[number]['login'];
	search: {redirect?: string};
};

function resolveLoginRedirect({pathname, href}: {pathname: string; href: string}): LoginRedirect {
	const app = APP_LOGINS.find(({home}) => pathname === home || pathname.startsWith(`${home}/`));

	if (app === undefined) {
		// Outside any known app — the root included. Each login page only accepts a `redirect` into its own
		// app, so passing this href on would fail the login route's own search validation and render the
		// generic error page instead of a form. Tasklist is where /_shadcn/_auth/ sends the root anyway.
		return {to: TASKLIST.login, search: {}};
	}

	return {
		to: app.login,
		search: href === app.home ? {} : {redirect: href},
	};
}

export {resolveLoginRedirect};
