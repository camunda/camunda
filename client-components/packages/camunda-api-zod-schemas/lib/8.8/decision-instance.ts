import {z} from 'zod';
import {
	API_VERSION,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	advancedDateTimeFilterSchema,
	basicStringFilterSchema,
	type Endpoint,
} from './common';
import {evaluatedDecisionInputItemSchema, matchedDecisionRuleItemSchema} from './decision-definition';

const decisionDefinitionTypeSchema = z.enum(['DECISION_TABLE', 'LITERAL_EXPRESSION', 'UNSPECIFIED', 'UNKNOWN']);
type DecisionDefinitionType = z.infer<typeof decisionDefinitionTypeSchema>;

const decisionInstanceStateSchema = z.enum(['EVALUATED', 'FAILED', 'UNSPECIFIED', 'UNKNOWN']);
type DecisionInstanceState = z.infer<typeof decisionInstanceStateSchema>;

const decisionInstanceSchema = z.object({
	decisionInstanceId: z.string(),
	state: decisionInstanceStateSchema,
	evaluationDate: z.string(),
	evaluationFailure: z.string(),
	decisionDefinitionId: z.string(),
	decisionDefinitionName: z.string(),
	decisionDefinitionVersion: z.number(),
	decisionDefinitionType: decisionDefinitionTypeSchema,
	result: z.string(),
	tenantId: z.string(),
	decisionInstanceKey: z.string(),
	processDefinitionKey: z.string(),
	processInstanceKey: z.string(),
	decisionDefinitionKey: z.string(),
	elementInstanceKey: z.string(),
});
type DecisionInstance = z.infer<typeof decisionInstanceSchema>;

const queryDecisionInstancesRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'decisionInstanceKey',
		'decisionInstanceId',
		'state',
		'evaluationDate',
		'evaluationFailure',
		'processDefinitionKey',
		'processInstanceKey',
		'processInstanceId',
		'decisionDefinitionKey',
		'decisionDefinitionId',
		'decisionDefinitionName',
		'decisionDefinitionVersion',
		'decisionDefinitionType',
		'tenantId',
		'elementInstanceKey',
	] as const,
	filter: z
		.object({
			evaluationDate: advancedDateTimeFilterSchema,
			decisionDefinitionKey: basicStringFilterSchema,
			...decisionInstanceSchema.pick({
				decisionInstanceId: true,
				state: true,
				evaluationFailure: true,
				decisionDefinitionId: true,
				decisionDefinitionName: true,
				decisionDefinitionVersion: true,
				decisionDefinitionType: true,
				tenantId: true,
				decisionInstanceKey: true,
				processDefinitionKey: true,
				processInstanceKey: true,
				elementInstanceKey: true,
			}).shape,
		})
		.partial(),
});
type QueryDecisionInstancesRequestBody = z.infer<typeof queryDecisionInstancesRequestBodySchema>;

const queryDecisionInstancesResponseBodySchema = getQueryResponseBodySchema(decisionInstanceSchema);
type QueryDecisionInstancesResponseBody = z.infer<typeof queryDecisionInstancesResponseBodySchema>;

const getDecisionInstanceResponseBodySchema = z.object({
	evaluatedInputs: z.array(evaluatedDecisionInputItemSchema).optional(),
	matchedRules: z.array(matchedDecisionRuleItemSchema).optional(),
	...decisionInstanceSchema.shape,
});
type GetDecisionInstanceResponseBody = z.infer<typeof getDecisionInstanceResponseBodySchema>;

const queryDecisionInstances: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/decision-instances/search`,
};

const getDecisionInstance: Endpoint<Pick<DecisionInstance, 'decisionInstanceId'>> = {
	method: 'GET',
	getUrl: ({decisionInstanceId}) => `/${API_VERSION}/decision-instances/${decisionInstanceId}`,
};

export {
	decisionDefinitionTypeSchema,
	decisionInstanceStateSchema,
	decisionInstanceSchema,
	queryDecisionInstancesRequestBodySchema,
	queryDecisionInstancesResponseBodySchema,
	getDecisionInstanceResponseBodySchema,
	queryDecisionInstances,
	getDecisionInstance,
};
export type {
	DecisionDefinitionType,
	DecisionInstanceState,
	DecisionInstance,
	QueryDecisionInstancesRequestBody,
	QueryDecisionInstancesResponseBody,
	GetDecisionInstanceResponseBody,
};
