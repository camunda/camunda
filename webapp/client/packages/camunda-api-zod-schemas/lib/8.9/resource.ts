/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';

const processDeploymentSchema = z.object({
	processDefinitionId: z.string(),
	processDefinitionVersion: z.number().int(),
	resourceName: z.string(),
	tenantId: z.string(),
	processDefinitionKey: z.string(),
});
type ProcessDeployment = z.infer<typeof processDeploymentSchema>;

const decisionDeploymentSchema = z.object({
	decisionDefinitionId: z.string(),
	version: z.number().int(),
	name: z.string(),
	tenantId: z.string(),
	decisionRequirementsId: z.string(),
	decisionDefinitionKey: z.string(),
	decisionRequirementsKey: z.string(),
});
type DecisionDeployment = z.infer<typeof decisionDeploymentSchema>;

const decisionRequirementsDeploymentSchema = z.object({
	decisionRequirementsId: z.string(),
	version: z.number().int(),
	decisionRequirementsName: z.string(),
	tenantId: z.string(),
	resourceName: z.string(),
	decisionRequirementsKey: z.string(),
});
type DecisionRequirementsDeployment = z.infer<typeof decisionRequirementsDeploymentSchema>;

const formDeploymentSchema = z.object({
	formId: z.string(),
	version: z.number().int(),
	resourceName: z.string(),
	tenantId: z.string(),
	formKey: z.string(),
});
type FormDeployment = z.infer<typeof formDeploymentSchema>;

const resourceDeploymentSchema = z.object({
	resourceId: z.string(),
	version: z.number().int(),
	resourceName: z.string(),
	tenantId: z.string(),
	resourceKey: z.string(),
});
type ResourceDeployment = z.infer<typeof resourceDeploymentSchema>;

const deploymentSchema = z.object({
	processDefinition: processDeploymentSchema.nullable(),
	decisionDefinition: decisionDeploymentSchema.nullable(),
	decisionRequirements: decisionRequirementsDeploymentSchema.nullable(),
	form: formDeploymentSchema.nullable(),
	resource: resourceDeploymentSchema.nullable(),
});
type Deployment = z.infer<typeof deploymentSchema>;

const createDeploymentResponseBodySchema = z.object({
	tenantId: z.string(),
	deploymentKey: z.string(),
	deployments: z.array(deploymentSchema),
});
type CreateDeploymentResponseBody = z.infer<typeof createDeploymentResponseBodySchema>;

const deleteResourceRequestBodySchema = z
	.object({
		operationReference: z.number().int().min(1).optional(),
		deleteHistory: z.boolean().optional().default(false),
	})
	.optional();
type DeleteResourceRequestBody = z.infer<typeof deleteResourceRequestBodySchema>;

const batchOperationCreatedResultSchema = z.object({
	batchOperationKey: z.string(),
	batchOperationType: z.string(),
});
type BatchOperationCreatedResult = z.infer<typeof batchOperationCreatedResultSchema>;

const deleteResourceResponseBodySchema = z.object({
	resourceKey: z.string(),
	batchOperation: batchOperationCreatedResultSchema.nullable(),
});
type DeleteResourceResponseBody = z.infer<typeof deleteResourceResponseBodySchema>;

const resourceSchema = z.object({
	resourceName: z.string(),
	version: z.number().int(),
	versionTag: z.string(),
	resourceId: z.string(),
	tenantId: z.string(),
	resourceKey: z.string(),
});
type Resource = z.infer<typeof resourceSchema>;

const getResourceContentResponseBodySchema = z.string();
type GetResourceContentResponseBody = z.infer<typeof getResourceContentResponseBodySchema>;

const createDeployment: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/deployments`;
	},
};

const deleteResource: Endpoint<{resourceKey: string}> = {
	method: 'POST',
	getUrl(params) {
		const {resourceKey} = params;

		return `/${API_VERSION}/resources/${resourceKey}/deletion`;
	},
};

const getResource: Endpoint<{resourceKey: string}> = {
	method: 'GET',
	getUrl(params) {
		const {resourceKey} = params;

		return `/${API_VERSION}/resources/${resourceKey}`;
	},
};

const getResourceContent: Endpoint<{resourceKey: string}> = {
	method: 'GET',
	getUrl(params) {
		const {resourceKey} = params;

		return `/${API_VERSION}/resources/${resourceKey}/content`;
	},
};

export {
	createDeployment,
	deleteResource,
	getResource,
	getResourceContent,
	createDeploymentResponseBodySchema,
	deleteResourceRequestBodySchema,
	deleteResourceResponseBodySchema,
	batchOperationCreatedResultSchema,
	resourceSchema,
	getResourceContentResponseBodySchema,
	processDeploymentSchema,
	decisionDeploymentSchema,
	decisionRequirementsDeploymentSchema,
	formDeploymentSchema,
	resourceDeploymentSchema,
	deploymentSchema,
};
export type {
	CreateDeploymentResponseBody,
	DeleteResourceRequestBody,
	DeleteResourceResponseBody,
	BatchOperationCreatedResult,
	Resource,
	GetResourceContentResponseBody,
	ProcessDeployment,
	DecisionDeployment,
	DecisionRequirementsDeployment,
	FormDeployment,
	ResourceDeployment,
	Deployment,
};
