/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet} from '@tanstack/react-router';
import {ForbiddenPage} from '#/shared/pages/ForbiddenPage';
import {ComponentNotAvailableError, ForbiddenError} from '#/shared/errors';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {NotFoundPage} from '#/shared/pages/NotFoundPage';
import {TasklistNavLayout} from '#/tasklist/modules/navigation/components/TasklistNavLayout';
import {featureFlags} from '#/shared/feature-flags';

// DS-only. The navigation rail wraps every Tasklist page from here so it is
// present on both Tasks and Processes. On the flag-off path this route keeps
// rendering a bare Outlet, and the Carbon filter panel stays inside the tasks
// layout where it has always been (TasksLayoutPage.tsx).
function RouteComponent() {
	if (!featureFlags.dsTasklistUI) {
		return <Outlet />;
	}

	return (
		<TasklistNavLayout>
			<Outlet />
		</TasklistNavLayout>
	);
}

export const Route = createFileRoute('/_auth/tasklist')({
	component: RouteComponent,
	beforeLoad: () => {
		if (!getClientConfig().components.active.includes('tasklist')) {
			throw new ComponentNotAvailableError('tasklist');
		}
	},
	errorComponent: ({error}) => {
		if (error instanceof ComponentNotAvailableError || error instanceof ForbiddenError) {
			return (
				<main id="main-content" className="cds--content">
					<ForbiddenPage />
				</main>
			);
		}

		throw error;
	},
	notFoundComponent: () => (
		<main id="main-content" className="cds--content">
			<NotFoundPage />
		</main>
	),
	head: () => ({
		meta: [
			{
				title: 'Tasklist - Camunda',
			},
		],
	}),
});
