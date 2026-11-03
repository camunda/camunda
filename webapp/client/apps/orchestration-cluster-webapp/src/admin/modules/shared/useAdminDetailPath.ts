/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRouter} from '@tanstack/react-router';

function useAdminDetailPath() {
	const router = useRouter();

	return {
		user: (username: string) => router.buildLocation({to: '/admin/users/$username', params: {username}}).pathname,
		group: (groupId: string) => router.buildLocation({to: '/admin/groups/$groupId', params: {groupId}}).pathname,
		role: (roleId: string) => router.buildLocation({to: '/admin/roles/$roleId', params: {roleId}}).pathname,
		tenant: (tenantId: string) => router.buildLocation({to: '/admin/tenants/$tenantId', params: {tenantId}}).pathname,
	};
}

export {useAdminDetailPath};
