/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {usageMetricsResponseItemSchema} from './gen';

const usageMetricsSchema = usageMetricsResponseItemSchema;
type UsageMetrics = z.infer<typeof usageMetricsSchema>;

const getUsageMetricsResponseBodySchema = usageMetricsSchema;
type GetUsageMetricsResponseBody = z.infer<typeof getUsageMetricsResponseBodySchema>;

type GetUsageMetricsParams = {
	startTime: string;
	endTime: string;
};

const getUsageMetrics: Endpoint<GetUsageMetricsParams> = {
	method: 'GET',
	getUrl: ({startTime, endTime}) =>
		`/${API_VERSION}/usage-metrics?${new URLSearchParams({
			startTime,
			endTime,
		}).toString()}`,
};

export {getUsageMetrics, usageMetricsSchema, getUsageMetricsResponseBodySchema};
export type {UsageMetrics, GetUsageMetricsResponseBody, GetUsageMetricsParams};
