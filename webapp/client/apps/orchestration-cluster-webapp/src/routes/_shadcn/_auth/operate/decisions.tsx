/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet} from '@tanstack/react-router';
import {ComponentAccessDeniedError, ComponentNotAvailableError, ForbiddenError} from '#/shared/errors';
import {assertComponentAccessible} from '#/shared/componentAccess';
import {queries} from '#/shared/http/queries';
import {ComponentAccessDeniedPage} from '#/shared/pages/shadcn.components/ComponentAccessDeniedPage';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';
import {PageLayout} from '@camunda/design-system';

export const Route = createFileRoute('/_shadcn/_auth/operate/decisions')({
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
	component: () => <Outlet />,
	head: () => ({
		meta: [
			{
				title: 'Operate - Camunda',
			},
		],
	}),
});
