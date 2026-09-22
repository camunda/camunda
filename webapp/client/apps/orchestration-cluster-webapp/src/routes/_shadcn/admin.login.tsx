/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, isRedirect, redirect} from '@tanstack/react-router';
import {z} from 'zod';
import {AdminLoginPage} from '#/admin/pages/AdminLoginPage';
import {queries} from '#/shared/http/queries';

const Route = createFileRoute('/_shadcn/admin/login')({
	validateSearch: z.object({
		redirect: z
			.string()
			.refine(
				(value) => /^\/admin(?:[/?#]|$)/.test(value) && !/^\/admin\/login(?:[/?#]|$)/.test(value),
				'Redirect must be an Admin path',
			)
			.optional(),
	}),
	beforeLoad: async ({search, context: {queryClient}}) => {
		try {
			await queryClient.ensureQueryData(queries.getCurrentUser());
			throw redirect({href: search.redirect ?? '/admin', replace: true});
		} catch (error) {
			if (isRedirect(error)) {
				throw error;
			}
		}
	},
	component: AdminLoginPage,
});

export {Route};
