/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {ForbiddenPage} from '#/shared/pages/ForbiddenPage';
import {ComponentNotAvailableError, ForbiddenError} from '#/shared/errors';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {NotFoundPage} from '#/shared/pages/NotFoundPage';

export const Route = createFileRoute('/_auth/tasklist')({
	beforeLoad: () => {
		if (!getClientConfig().components.active.includes('tasklist')) {
			throw new ComponentNotAvailableError('tasklist');
		}
	},
	errorComponent: ({error}) => {
		if (error instanceof ComponentNotAvailableError || error instanceof ForbiddenError) {
			return <ForbiddenPage />;
		}
		throw error;
	},
	notFoundComponent: () => (
		<main className="cds--content">
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
