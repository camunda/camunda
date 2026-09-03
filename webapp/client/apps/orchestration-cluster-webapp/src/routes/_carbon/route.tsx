/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet} from '@tanstack/react-router';
import {ThemeProvider} from '#/shared/theme/ThemeProvider';
import {NotFoundPage} from '#/shared/pages/NotFoundPage';
import {GenericErrorPage} from '#/shared/pages/GenericErrorPage';
import {Notifications} from '#/shared/notifications/components/Notifications';
import {NetworkStatusWatcher} from '#/shared/notifications/components/NetworkStatusWatcher';
import './index.scss';

const Route = createFileRoute('/_carbon')({
	notFoundComponent: () => (
		<ThemeProvider>
			<NotFoundPage />
		</ThemeProvider>
	),
	errorComponent: ({reset}) => (
		<ThemeProvider>
			<GenericErrorPage reset={reset} />
		</ThemeProvider>
	),
	component: function CarbonLayout() {
		return (
			<ThemeProvider>
				<Notifications />
				<NetworkStatusWatcher />
				<Outlet />
			</ThemeProvider>
		);
	},
});

export {Route};
