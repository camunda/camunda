import {z} from 'zod';
import {
	API_VERSION,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	getEnumFilterSchema,
	type Endpoint,
} from './common';

const elementInstanceStateSchema = z.enum(['ACTIVE', 'COMPLETED', 'TERMINATED']);
type ElementInstanceState = z.infer<typeof elementInstanceStateSchema>;

const elementInstanceTypeSchema = z.enum([
	'UNSPECIFIED',
	'PROCESS',
	'SUB_PROCESS',
	'EVENT_SUB_PROCESS',
	'AD_HOC_SUB_PROCESS',
	'START_EVENT',
	'INTERMEDIATE_CATCH_EVENT',
	'INTERMEDIATE_THROW_EVENT',
	'BOUNDARY_EVENT',
	'END_EVENT',
	'SERVICE_TASK',
	'RECEIVE_TASK',
	'USER_TASK',
	'MANUAL_TASK',
	'TASK',
	'EXCLUSIVE_GATEWAY',
	'INCLUSIVE_GATEWAY',
	'PARALLEL_GATEWAY',
	'EVENT_BASED_GATEWAY',
	'SEQUENCE_FLOW',
	'MULTI_INSTANCE_BODY',
	'CALL_ACTIVITY',
	'BUSINESS_RULE_TASK',
	'SCRIPT_TASK',
	'SEND_TASK',
	'UNKNOWN',
]);
type ElementInstanceType = z.infer<typeof elementInstanceTypeSchema>;

const elementInstanceSchema = z.object({
	processDefinitionId: z.string(),
	startDate: z.string(),
	endDate: z.string().optional(),
	elementId: z.string(),
	elementName: z.string(),
	type: elementInstanceTypeSchema,
	state: elementInstanceStateSchema,
	hasIncident: z.boolean(),
	tenantId: z.string(),
	elementInstanceKey: z.string(),
	processInstanceKey: z.string(),
	processDefinitionKey: z.string(),
	incidentKey: z.string().optional(),
});
type ElementInstance = z.infer<typeof elementInstanceSchema>;

const elementInstanceFilterSchema = z
	.object({
		processDefinitionId: z.string(),
		state: z.union([elementInstanceStateSchema, getEnumFilterSchema(elementInstanceStateSchema)]),
		type: elementInstanceTypeSchema,
		elementId: z.string(),
		elementName: z.string(),
		hasIncident: z.boolean(),
		tenantId: z.string(),
		elementInstanceKey: z.string(),
		processInstanceKey: z.string(),
		processDefinitionKey: z.string(),
		incidentKey: z.string(),
		scopeKey: z.string(),
	})
	.partial();

const queryElementInstancesRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'elementInstanceKey',
		'processInstanceKey',
		'processDefinitionKey',
		'processDefinitionId',
		'startDate',
		'endDate',
		'elementId',
		'elementName',
		'type',
		'state',
		'incidentKey',
		'tenantId',
	] as const,
	filter: elementInstanceFilterSchema,
});
type QueryElementInstancesRequestBody = z.infer<typeof queryElementInstancesRequestBodySchema>;

const queryElementInstancesResponseBodySchema = getQueryResponseBodySchema(elementInstanceSchema);
type QueryElementInstancesResponseBody = z.infer<typeof queryElementInstancesResponseBodySchema>;

const queryElementInstances: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/element-instances/search`;
	},
};

const getElementInstance: Endpoint<Pick<ElementInstance, 'elementInstanceKey'>> = {
	method: 'GET',
	getUrl(params) {
		const {elementInstanceKey} = params;
		return `/${API_VERSION}/element-instances/${elementInstanceKey}`;
	},
};

const getElementInstanceResponseBodySchema = elementInstanceSchema;
type GetElementInstanceResponseBody = z.infer<typeof getElementInstanceResponseBodySchema>;

const updateElementInstanceVariablesRequestBodySchema = z.object({
	variables: z.record(z.string(), z.unknown()),
	local: z.boolean().optional(),
});
type UpdateElementInstanceVariablesRequestBody = z.infer<typeof updateElementInstanceVariablesRequestBodySchema>;

const updateElementInstanceVariables: Endpoint<Pick<ElementInstance, 'elementInstanceKey'>> = {
	method: 'PUT',
	getUrl(params) {
		const {elementInstanceKey} = params;
		return `/${API_VERSION}/element-instances/${elementInstanceKey}/variables`;
	},
};

export {
	queryElementInstances,
	getElementInstance,
	updateElementInstanceVariables,
	queryElementInstancesRequestBodySchema,
	queryElementInstancesResponseBodySchema,
	getElementInstanceResponseBodySchema,
	updateElementInstanceVariablesRequestBodySchema,
	elementInstanceStateSchema,
	elementInstanceTypeSchema,
	elementInstanceSchema,
	elementInstanceFilterSchema,
};
export type {
	ElementInstanceState,
	ElementInstanceType,
	ElementInstance,
	QueryElementInstancesRequestBody,
	QueryElementInstancesResponseBody,
	GetElementInstanceResponseBody,
	UpdateElementInstanceVariablesRequestBody,
};
