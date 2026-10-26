/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMatchRoute, type RegisteredRouter} from '@tanstack/react-router';

type FileRouteTypes = RegisteredRouter['routeTree']['types']['fileRouteTypes'];

const COMPONENT_HOME_ROUTES = [
	{home: '/admin', matches: ['/admin']},
	{home: '/tasklist', matches: ['/tasklist']},
	{home: '/operate-preview', matches: ['/operate-preview', '/operate/decisions']},
] as const satisfies ReadonlyArray<{home: FileRouteTypes['to']; matches: ReadonlyArray<FileRouteTypes['to']>}>;

function useActiveComponentHomeRoute(): FileRouteTypes['to'] | undefined {
	const matchRoute = useMatchRoute();

	return COMPONENT_HOME_ROUTES.find(({matches}) => matches.some((to) => matchRoute({to, fuzzy: true}) !== false))?.home;
}

export {useActiveComponentHomeRoute};
