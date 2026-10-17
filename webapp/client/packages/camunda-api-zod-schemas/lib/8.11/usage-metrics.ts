/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {usageMetricsResponseSchema} from './gen/zod/usageMetricsResponseSchema';
import type {GetUsageMetricsQuery} from './gen/types/GetUsageMetrics';
import type {UsageMetricsResponse} from './gen/types/UsageMetricsResponse';

const usageMetricsSchema = usageMetricsResponseSchema;
type UsageMetrics = UsageMetricsResponse;

const getUsageMetricsResponseBodySchema = usageMetricsSchema;
type GetUsageMetricsResponseBody = UsageMetricsResponse;

type GetUsageMetricsParams = GetUsageMetricsQuery;

const getUsageMetrics = {
	method: 'GET',
	getUrl: ({startTime, endTime, tenantId, withTenants}) => {
		const queryParams = new URLSearchParams({startTime, endTime});
		if (tenantId !== undefined) {
			queryParams.set('tenantId', tenantId);
		}
		if (withTenants !== undefined) {
			queryParams.set('withTenants', String(withTenants));
		}

		return `/${API_VERSION}/system/usage-metrics?${queryParams.toString()}` as const;
	},
} as const satisfies Endpoint<GetUsageMetricsParams>;

export {getUsageMetrics, usageMetricsSchema, getUsageMetricsResponseBodySchema};
export type {UsageMetrics, GetUsageMetricsResponseBody, GetUsageMetricsParams};
