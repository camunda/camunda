import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';

const usageMetricsSchema = z.object({
	assignees: z.number().int(),
	processInstances: z.number().int(),
	decisionInstances: z.number().int(),
});
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
