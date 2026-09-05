/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import appCss from '#/shared/theme/tailwind.css?url';
import {createFileRoute, Outlet} from '@tanstack/react-router';
import {ThemeProvider} from '#/shared/theme/shadcn.components/ThemeProvider';

const Route = createFileRoute('/shadcn')({
	component: function RouteComponent() {
		return (
			<ThemeProvider>
				<Outlet />
			</ThemeProvider>
		);
	},
	head: () => ({
		meta: [],
		links: [{rel: 'stylesheet', href: appCss}],
	}),
});

export {Route};
