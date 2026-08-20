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
import {ComponentAccessDeniedPage} from '#/shared/pages/ComponentAccessDeniedPage';
import {ForbiddenPage} from '#/shared/pages/ForbiddenPage';
import {NotFoundPage} from '#/shared/pages/NotFoundPage';

export const Route = createFileRoute('/_carbon/_auth/admin')({
	beforeLoad: async ({context: {queryClient}}) => {
		const {authorizedComponents} = await queryClient.ensureQueryData(queries.getCurrentUser());
		assertComponentAccessible('admin', authorizedComponents);
	},
	errorComponent: ({error}) => {
		if (error instanceof ComponentAccessDeniedError) {
			return (
				<main id="main-content" className="cds--content">
					<ComponentAccessDeniedPage />
				</main>
			);
		}

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
				title: 'Admin - Camunda',
			},
		],
	}),
});
