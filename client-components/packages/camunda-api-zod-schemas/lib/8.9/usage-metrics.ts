/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';

const usageMetricsItemSchema = z.object({
	assignees: z.number().int(),
	processInstances: z.number().int(),
	decisionInstances: z.number().int(),
});
const usageMetricsSchema = usageMetricsItemSchema.extend({
	activeTenants: z.number().int(),
	tenants: z.record(z.string(), usageMetricsItemSchema),
});
type UsageMetrics = z.infer<typeof usageMetricsSchema>;

const getUsageMetricsResponseBodySchema = usageMetricsSchema;
type GetUsageMetricsResponseBody = z.infer<typeof getUsageMetricsResponseBodySchema>;

type GetUsageMetricsParams = {
	startTime: string;
	endTime: string;
	tenantId?: string;
	withTenants?: boolean;
};

const getUsageMetrics: Endpoint<GetUsageMetricsParams> = {
	method: 'GET',
	getUrl: ({startTime, endTime, tenantId, withTenants}) => {
		const queryParams = new URLSearchParams({startTime, endTime});
		if (tenantId !== undefined) {
			queryParams.set('tenantId', tenantId);
		}
		if (withTenants !== undefined) {
			queryParams.set('withTenants', String(withTenants));
		}

		return `/${API_VERSION}/system/usage-metrics?${queryParams.toString()}`;
	},
};

export {getUsageMetrics, usageMetricsSchema, getUsageMetricsResponseBodySchema};
export type {UsageMetrics, GetUsageMetricsResponseBody, GetUsageMetricsParams};
