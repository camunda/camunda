/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {RouterContextProvider, createMemoryHistory, createRootRoute, createRouter} from '@tanstack/react-router';

function createRouterContextWrapper() {
	const router = createRouter({
		routeTree: createRootRoute(),
		history: createMemoryHistory({initialEntries: ['/']}),
	});

	const RouterContextWrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
		<RouterContextProvider router={router}>{children}</RouterContextProvider>
	);

	return RouterContextWrapper;
}

export {createRouterContextWrapper};
