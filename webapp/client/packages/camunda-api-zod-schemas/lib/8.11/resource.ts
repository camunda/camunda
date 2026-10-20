/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from '../common';
import {batchOperationCreatedResultSchema} from './gen/zod/batchOperationCreatedResultSchema';
import {deleteResourceBodySchema} from './gen/zod/deleteResourceSchema';
import {deleteResourceResponseSchema} from './gen/zod/deleteResourceResponseSchema';
import {deploymentDecisionRequirementsResultSchema} from './gen/zod/deploymentDecisionRequirementsResultSchema';
import {deploymentDecisionResultSchema} from './gen/zod/deploymentDecisionResultSchema';
import {deploymentFormResultSchema} from './gen/zod/deploymentFormResultSchema';
import {deploymentMetadataResultSchema} from './gen/zod/deploymentMetadataResultSchema';
import {deploymentProcessResultSchema} from './gen/zod/deploymentProcessResultSchema';
import {deploymentResourceResultSchema} from './gen/zod/deploymentResourceResultSchema';
import {deploymentResultSchema} from './gen/zod/deploymentResultSchema';
import {getResourceContentStatus200Schema} from './gen/zod/getResourceContentSchema';
import {resourceResultSchema} from './gen/zod/resourceResultSchema';
import type {BatchOperationCreatedResult} from './gen/types/BatchOperationCreatedResult';
import type {DeleteResourceBody} from './gen/types/DeleteResource';
import type {DeleteResourceResponse} from './gen/types/DeleteResourceResponse';
import type {DeploymentDecisionRequirementsResult} from './gen/types/DeploymentDecisionRequirementsResult';
import type {DeploymentDecisionResult} from './gen/types/DeploymentDecisionResult';
import type {DeploymentFormResult} from './gen/types/DeploymentFormResult';
import type {DeploymentMetadataResult} from './gen/types/DeploymentMetadataResult';
import type {DeploymentProcessResult} from './gen/types/DeploymentProcessResult';
import type {DeploymentResourceResult} from './gen/types/DeploymentResourceResult';
import type {DeploymentResult} from './gen/types/DeploymentResult';
import type {GetResourceContentStatus200} from './gen/types/GetResourceContent';
import type {ResourceResult} from './gen/types/ResourceResult';

const processDeploymentSchema = deploymentProcessResultSchema;
type ProcessDeployment = DeploymentProcessResult;

const decisionDeploymentSchema = deploymentDecisionResultSchema;
type DecisionDeployment = DeploymentDecisionResult;

const decisionRequirementsDeploymentSchema = deploymentDecisionRequirementsResultSchema;
type DecisionRequirementsDeployment = DeploymentDecisionRequirementsResult;

const formDeploymentSchema = deploymentFormResultSchema;
type FormDeployment = DeploymentFormResult;

const resourceDeploymentSchema = deploymentResourceResultSchema;
type ResourceDeployment = DeploymentResourceResult;

const deploymentSchema = deploymentMetadataResultSchema;
type Deployment = DeploymentMetadataResult;

const createDeploymentResponseBodySchema = deploymentResultSchema;
type CreateDeploymentResponseBody = DeploymentResult;

// The request body is optional in the spec, so the op body schema (nullish) is used here.
const deleteResourceRequestBodySchema = deleteResourceBodySchema;
type DeleteResourceRequestBody = DeleteResourceBody;

const deleteResourceResponseBodySchema = deleteResourceResponseSchema;
type DeleteResourceResponseBody = DeleteResourceResponse;

const resourceSchema = resourceResultSchema;
type Resource = ResourceResult;

const getResourceContentResponseBodySchema = getResourceContentStatus200Schema;
type GetResourceContentResponseBody = GetResourceContentStatus200;

const createDeployment = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/deployments` as const;
	},
} as const satisfies Endpoint;

const deleteResource = {
	method: 'POST',
	getUrl(params) {
		const {resourceKey} = params;

		return `/${API_VERSION}/resources/${resourceKey}/deletion` as const;
	},
} as const satisfies Endpoint<{resourceKey: string}>;

const getResource = {
	method: 'GET',
	getUrl(params) {
		const {resourceKey} = params;

		return `/${API_VERSION}/resources/${resourceKey}` as const;
	},
} as const satisfies Endpoint<{resourceKey: string}>;

const getResourceContent = {
	method: 'GET',
	getUrl(params) {
		const {resourceKey} = params;

		return `/${API_VERSION}/resources/${resourceKey}/content` as const;
	},
} as const satisfies Endpoint<{resourceKey: string}>;

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
