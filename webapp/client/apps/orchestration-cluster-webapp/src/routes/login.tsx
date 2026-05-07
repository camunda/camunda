/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, isRedirect, redirect} from '@tanstack/react-router';
import {z} from 'zod';
import {queries} from '#/modules/queries';
import {Login} from '../pages/login';

const searchSchema = z.object({
	redirect: z.string().optional(),
});

export const Route = createFileRoute('/login')({
	validateSearch: searchSchema.parse,
	beforeLoad: async ({context, search}) => {
		try {
			await context.queryClient.ensureQueryData(queries.currentUser);
			throw redirect({to: (search.redirect ?? '/') as never});
		} catch (e) {
			if (isRedirect(e)) throw e;
		}
	},
	component: Login,
});
