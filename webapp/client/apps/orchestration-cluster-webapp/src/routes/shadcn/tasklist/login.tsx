/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, isRedirect, redirect} from '@tanstack/react-router';
import {z} from 'zod';
import {queries} from '#/shared/http/queries';
import {TasklistLoginPage} from '#/tasklist/pages/shadcn.components/TasklistLoginPage';

const Route = createFileRoute('/shadcn/tasklist/login')({
	validateSearch: z.object({
		redirect: z
			.string()
			.refine(
				(value) => /^\/shadcn(?:[/?#]|$)/.test(value) && !/^\/shadcn\/tasklist\/login(?:[/?#]|$)/.test(value),
				'Redirect must be a shadcn path',
			)
			.optional(),
	}),
	beforeLoad: async ({search, context: {queryClient}}) => {
		try {
			await queryClient.ensureQueryData(queries.getCurrentUser());
			throw redirect({href: search.redirect ?? '/shadcn/tasklist', replace: true});
		} catch (error) {
			if (isRedirect(error)) {
				throw error;
			}
		}
	},
	component: TasklistLoginPage,
});

export {Route};
