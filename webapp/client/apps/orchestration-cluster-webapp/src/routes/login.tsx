/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Login} from '#/pages/login';
import {createFileRoute, isRedirect, redirect} from '@tanstack/react-router';
import {z} from 'zod';
import {currentUserQueryOptions} from '#/modules/queries/currentUser.query';

export const Route = createFileRoute('/login')({
	validateSearch: z.object({
		redirect: z
			.string()
			.refine((val) => val.startsWith('/'), 'Redirect must be a relative path')
			.optional(),
	}),
	beforeLoad: async ({search, context: {queryClient}}) => {
		try {
			await queryClient.ensureQueryData(currentUserQueryOptions);
			throw redirect({href: search.redirect ?? '/', replace: true});
		} catch (e) {
			if (isRedirect(e)) throw e;
			// Not authenticated — show login form
		}
	},
	component: Login,
});
