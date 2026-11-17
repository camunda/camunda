/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {useMatchRoute, type RegisteredRouter} from '@tanstack/react-router';

type FileRouteTypes = RegisteredRouter['routeTree']['types']['fileRouteTypes'];

function useHasRouteMatch() {
	const matchRoute = useMatchRoute();

	return useCallback(
		(...routes: FileRouteTypes['to'][]) => routes.some((to) => matchRoute({to}) !== false),
		[matchRoute],
	);
}

export {useHasRouteMatch};
