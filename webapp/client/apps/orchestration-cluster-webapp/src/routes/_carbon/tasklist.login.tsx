/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {LoginPage} from '#/shared/pages/LoginPage';
import {createFileRoute, isRedirect, redirect} from '@tanstack/react-router';
import {z} from 'zod';
import {queries} from '#/shared/http/queries';

export const Route = createFileRoute('/_carbon/tasklist/login')({
	validateSearch: z.object({
		redirect: z
			.string()
			.refine(
				(value) => /^\/tasklist(?:[/?#]|$)/.test(value) && !/^\/tasklist\/login(?:[/?#]|$)/.test(value),
				'Redirect must be a Tasklist path',
			)
			.optional(),
	}),
	beforeLoad: async ({search, context: {queryClient}}) => {
		try {
			await queryClient.ensureQueryData(queries.getCurrentUser());
			throw redirect({href: search.redirect ?? '/tasklist', replace: true});
		} catch (error) {
			if (isRedirect(error)) {
				throw error;
			}
		}
	},
	component: () => <LoginPage title="Tasklist" />,
});
