/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {
	deploymentProcessResultSchema,
	deploymentDecisionResultSchema,
	deploymentDecisionRequirementsResultSchema,
	deploymentFormResultSchema,
	deploymentResourceResultSchema,
	deploymentResultSchema,
	resourceResultSchema,
	deleteResourceRequestSchema,
	getResourceContent200Schema,
} from './gen';

const processDeploymentSchema = deploymentProcessResultSchema;
type ProcessDeployment = z.infer<typeof processDeploymentSchema>;

const decisionDeploymentSchema = deploymentDecisionResultSchema;
type DecisionDeployment = z.infer<typeof decisionDeploymentSchema>;

const decisionRequirementsDeploymentSchema = deploymentDecisionRequirementsResultSchema;
type DecisionRequirementsDeployment = z.infer<typeof decisionRequirementsDeploymentSchema>;

const formDeploymentSchema = deploymentFormResultSchema;
type FormDeployment = z.infer<typeof formDeploymentSchema>;

const resourceDeploymentSchema = deploymentResourceResultSchema;
type ResourceDeployment = z.infer<typeof resourceDeploymentSchema>;

const createDeploymentResponseBodySchema = deploymentResultSchema;
type CreateDeploymentResponseBody = z.infer<typeof createDeploymentResponseBodySchema>;

const deleteResourceRequestBodySchema = deleteResourceRequestSchema;
type DeleteResourceRequestBody = z.infer<typeof deleteResourceRequestBodySchema>;

const resourceSchema = resourceResultSchema;
type Resource = z.infer<typeof resourceSchema>;

const getResourceContentResponseBodySchema = getResourceContent200Schema;
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
	resourceSchema,
	getResourceContentResponseBodySchema,
	processDeploymentSchema,
	decisionDeploymentSchema,
	decisionRequirementsDeploymentSchema,
	formDeploymentSchema,
	resourceDeploymentSchema,
};
export type {
	CreateDeploymentResponseBody,
	DeleteResourceRequestBody,
	Resource,
	GetResourceContentResponseBody,
	ProcessDeployment,
	DecisionDeployment,
	DecisionRequirementsDeployment,
	FormDeployment,
	ResourceDeployment,
};
