/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, isRedirect, redirect} from '@tanstack/react-router';
import {AdminSetupPage} from '#/admin/pages/AdminSetupPage';
import {ADMIN_LOGIN} from '#/shared/auth/resolveLoginRedirect';
import {queries} from '#/shared/http/queries';

const Route = createFileRoute('/_shadcn/admin/setup')({
	beforeLoad: async ({context: {queryClient}}) => {
		try {
			await queryClient.ensureQueryData(queries.getCurrentUser());
			throw redirect({href: ADMIN_LOGIN.home, replace: true});
		} catch (error) {
			if (isRedirect(error)) {
				throw error;
			}
		}
	},
	component: AdminSetupPage,
});

export {Route};
