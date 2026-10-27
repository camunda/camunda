/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {ComponentAccessDeniedError, ComponentNotAvailableError, ForbiddenError} from '#/shared/errors';
import {assertComponentAccessible} from '#/shared/componentAccess';
import {queries} from '#/shared/http/queries';
import {ComponentAccessDeniedPage} from '#/shared/pages/shadcn.components/ComponentAccessDeniedPage';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';
import {PageLayout} from '@camunda/design-system';

// Migration-time only: this leaf, and its /operate-preview path, exist so the Operate
// Dashboard's DS port can run alongside the live Carbon /operate route without colliding
// with it. At cutover this directory is renamed to `operate`, `_carbon/_auth/operate/**`
// is deleted, and the `operate-preview` APP_ROUTES entry in `../route.tsx` is repointed
// back to `/operate` — see docs/migration/operate-dashboard-tiering.md.
export const Route = createFileRoute('/_shadcn/_auth/operate-preview')({
	beforeLoad: async ({context: {queryClient}}) => {
		const {authorizedComponents} = await queryClient.ensureQueryData(queries.getCurrentUser());
		assertComponentAccessible('operate', authorizedComponents);
	},
	errorComponent: ({error}) => {
		if (error instanceof ComponentAccessDeniedError) {
			return (
				<PageLayout>
					<ComponentAccessDeniedPage />
				</PageLayout>
			);
		}

		if (error instanceof ComponentNotAvailableError || error instanceof ForbiddenError) {
			return (
				<PageLayout>
					<ForbiddenPage />
				</PageLayout>
			);
		}
		throw error;
	},
	notFoundComponent: () => (
		<PageLayout>
			<NotFoundPage />
		</PageLayout>
	),
	head: () => ({
		meta: [
			{
				title: 'Operate - Camunda',
			},
		],
	}),
});
