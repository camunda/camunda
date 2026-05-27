/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';

const jobMetricsConfigurationSchema = z.object({
	enabled: z.boolean(),
	exportInterval: z.string(),
	maxWorkerNameLength: z.number().int(),
	maxJobTypeLength: z.number().int(),
	maxTenantIdLength: z.number().int(),
	maxUniqueKeys: z.number().int(),
});
type JobMetricsConfiguration = z.infer<typeof jobMetricsConfigurationSchema>;

const componentsConfigurationSchema = z.object({
	active: z.array(z.string()),
});
type ComponentsConfiguration = z.infer<typeof componentsConfigurationSchema>;

const deploymentConfigurationSchema = z.object({
	isMultiTenancyEnabled: z.boolean(),
	maxRequestSize: z.number(),
});
type DeploymentConfiguration = z.infer<typeof deploymentConfigurationSchema>;

const authenticationConfigurationSchema = z.object({
	canLogout: z.boolean(),
	isLoginDelegated: z.boolean(),
});
type AuthenticationConfiguration = z.infer<typeof authenticationConfigurationSchema>;

const cloudConfigurationSchema = z.object({
	stage: z.string().nullable(),
});
type CloudConfiguration = z.infer<typeof cloudConfigurationSchema>;

const systemConfigurationSchema = z.object({
	jobMetrics: jobMetricsConfigurationSchema,
	components: componentsConfigurationSchema,
	deployment: deploymentConfigurationSchema,
	authentication: authenticationConfigurationSchema,
	cloud: cloudConfigurationSchema,
});
type SystemConfiguration = z.infer<typeof systemConfigurationSchema>;

const getSystemConfigurationResponseBodySchema = systemConfigurationSchema;
type GetSystemConfigurationResponseBody = z.infer<typeof getSystemConfigurationResponseBodySchema>;

const getSystemConfiguration: Endpoint = {
	method: 'GET',
	getUrl: () => `/${API_VERSION}/system/configuration`,
};

export {
	jobMetricsConfigurationSchema,
	componentsConfigurationSchema,
	deploymentConfigurationSchema,
	authenticationConfigurationSchema,
	cloudConfigurationSchema,
	systemConfigurationSchema,
	getSystemConfigurationResponseBodySchema,
	getSystemConfiguration,
};
export type {
	JobMetricsConfiguration,
	ComponentsConfiguration,
	DeploymentConfiguration,
	AuthenticationConfiguration,
	CloudConfiguration,
	SystemConfiguration,
	GetSystemConfigurationResponseBody,
};
