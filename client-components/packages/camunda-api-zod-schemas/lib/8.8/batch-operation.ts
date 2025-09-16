import {z} from 'zod';
import {API_VERSION, getQueryRequestBodySchema, getQueryResponseBodySchema, type Endpoint} from './common';

const batchOperationTypeSchema = z.enum([
	'CANCEL_PROCESS_INSTANCE',
	'RESOLVE_INCIDENT',
	'MIGRATE_PROCESS_INSTANCE',
	'MODIFY_PROCESS_INSTANCE',
]);
type BatchOperationType = z.infer<typeof batchOperationTypeSchema>;

const batchOperationStateSchema = z.enum([
	'CREATED',
	'ACTIVE',
	'SUSPENDED',
	'COMPLETED',
	'PARTIALLY_COMPLETED',
	'CANCELED',
	'INCOMPLETED',
	'FAILED',
]);
type BatchOperationState = z.infer<typeof batchOperationStateSchema>;

const batchOperationItemStateSchema = z.enum(['ACTIVE', 'COMPLETED', 'CANCELED', 'FAILED']);
type BatchOperationItemState = z.infer<typeof batchOperationItemStateSchema>;

const batchOperationSchema = z.object({
	batchOperationKey: z.string(),
	state: batchOperationStateSchema,
	batchOperationType: batchOperationTypeSchema,
	startDate: z.string().optional(),
	endDate: z.string().optional(),
	operationsTotalCount: z.number().int(),
	operationsFailedCount: z.number().int(),
	operationsCompletedCount: z.number().int(),
});
type BatchOperation = z.infer<typeof batchOperationSchema>;

const batchOperationItemSchema = z.object({
	batchOperationKey: z.string(),
	itemKey: z.string(),
	processInstanceKey: z.string(),
	state: batchOperationItemStateSchema,
	processedDate: z.string().optional(),
	errorMessage: z.string().optional(),
});
type BatchOperationItem = z.infer<typeof batchOperationItemSchema>;

const queryBatchOperationsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['batchOperationKey', 'operationType', 'state', 'startDate', 'endDate'] as const,
	filter: z
		.object({
			batchOperationKey: z.string(),
			operationType: batchOperationTypeSchema,
			state: batchOperationStateSchema,
		})
		.partial(),
});
type QueryBatchOperationsRequestBody = z.infer<typeof queryBatchOperationsRequestBodySchema>;

const queryBatchOperationsResponseBodySchema = getQueryResponseBodySchema(batchOperationSchema);
type QueryBatchOperationsResponseBody = z.infer<typeof queryBatchOperationsResponseBodySchema>;

const queryBatchOperationItemsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['batchOperationKey', 'itemKey', 'processInstanceKey', 'state'] as const,
	filter: z
		.object({
			batchOperationKey: z.string(),
			itemKey: z.string(),
			processInstanceKey: z.string(),
			state: batchOperationItemStateSchema,
		})
		.partial(),
});
type QueryBatchOperationItemsRequestBody = z.infer<typeof queryBatchOperationItemsRequestBodySchema>;

const queryBatchOperationItemsResponseBodySchema = getQueryResponseBodySchema(batchOperationItemSchema);
type QueryBatchOperationItemsResponseBody = z.infer<typeof queryBatchOperationItemsResponseBodySchema>;

const getBatchOperation: Endpoint<{batchOperationKey: string}> = {
	method: 'GET',
	getUrl: ({batchOperationKey}) => `/${API_VERSION}/batch-operations/${batchOperationKey}`,
};

const queryBatchOperations: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/batch-operations/search`,
};

const cancelBatchOperation: Endpoint<{batchOperationKey: string}> = {
	method: 'PUT',
	getUrl: ({batchOperationKey}) => `/${API_VERSION}/batch-operations/${batchOperationKey}/cancellation`,
};

const suspendBatchOperation: Endpoint<{batchOperationKey: string}> = {
	method: 'PUT',
	getUrl: ({batchOperationKey}) => `/${API_VERSION}/batch-operations/${batchOperationKey}/suspension`,
};

const resumeBatchOperation: Endpoint<{batchOperationKey: string}> = {
	method: 'PUT',
	getUrl: ({batchOperationKey}) => `/${API_VERSION}/batch-operations/${batchOperationKey}/resumption`,
};

const queryBatchOperationItems: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/batch-operation-items/search`,
};

export {
	batchOperationTypeSchema,
	batchOperationStateSchema,
	batchOperationItemStateSchema,
	batchOperationSchema,
	batchOperationItemSchema,
	queryBatchOperationsRequestBodySchema,
	queryBatchOperationsResponseBodySchema,
	queryBatchOperationItemsRequestBodySchema,
	queryBatchOperationItemsResponseBodySchema,
	getBatchOperation,
	queryBatchOperations,
	cancelBatchOperation,
	suspendBatchOperation,
	resumeBatchOperation,
	queryBatchOperationItems,
};

export type {
	BatchOperationType,
	BatchOperationState,
	BatchOperationItemState,
	BatchOperation,
	BatchOperationItem,
	QueryBatchOperationsRequestBody,
	QueryBatchOperationsResponseBody,
	QueryBatchOperationItemsRequestBody,
	QueryBatchOperationItemsResponseBody,
};
