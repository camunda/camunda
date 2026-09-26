/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useQuery} from '@tanstack/react-query';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {queries} from '#/shared/http/queries';

function runningOrAllInstancesFilter(total: number) {
	const isEmpty = total === 0;
	return {active: true, incidents: true, completed: isEmpty, canceled: isEmpty, suspended: false};
}

function useDashboardTenants() {
	const isMultiTenancyEnabled = getClientConfig().deployment.isMultiTenancyEnabled;
	const {data} = useQuery({...queries.getCurrentUser(), enabled: isMultiTenancyEnabled});
	const tenantsById = useMemo(
		() => Object.fromEntries((data?.tenants ?? []).map(({tenantId, name}) => [tenantId, name])),
		[data?.tenants],
	);

	return {isMultiTenancyEnabled, tenantsById};
}

function dashboardTenantId(tenantId: string | null, isMultiTenancyEnabled: boolean) {
	return isMultiTenancyEnabled ? (tenantId ?? '<default>') : undefined;
}

export {runningOrAllInstancesFilter, dashboardTenantId, useDashboardTenants};
