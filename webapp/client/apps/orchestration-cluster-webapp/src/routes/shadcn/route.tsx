/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import appCss from '#/shared/theme/tailwind.css?url';
import {PageLayout} from '@camunda/design-system';
import {createFileRoute, Outlet} from '@tanstack/react-router';
import {ThemeProvider} from '#/shared/theme/shadcn.components/ThemeProvider';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';

const Route = createFileRoute('/shadcn')({
	component: function RouteComponent() {
		return (
			<ThemeProvider>
				<Outlet />
			</ThemeProvider>
		);
	},
	notFoundComponent: () => (
		<ThemeProvider>
			<PageLayout className="h-dvh">
				<NotFoundPage />
			</PageLayout>
		</ThemeProvider>
	),
	errorComponent: ({reset}) => (
		<ThemeProvider>
			<PageLayout className="h-dvh">
				<GenericErrorPage reset={reset} />
			</PageLayout>
		</ThemeProvider>
	),
	head: () => ({
		meta: [],
		links: [{rel: 'stylesheet', href: appCss}],
	}),
});

export {Route};
