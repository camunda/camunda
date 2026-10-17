/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {authenticationConfigurationResponseSchema} from './gen/zod/authenticationConfigurationResponseSchema';
import {cloudConfigurationResponseSchema} from './gen/zod/cloudConfigurationResponseSchema';
import {componentsConfigurationResponseSchema} from './gen/zod/componentsConfigurationResponseSchema';
import {deploymentConfigurationResponseSchema} from './gen/zod/deploymentConfigurationResponseSchema';
import {getSystemConfigurationStatus200Schema} from './gen/zod/getSystemConfigurationSchema';
import {jobMetricsConfigurationResponseSchema} from './gen/zod/jobMetricsConfigurationResponseSchema';
import {systemConfigurationResponseSchema} from './gen/zod/systemConfigurationResponseSchema';
import type {AuthenticationConfigurationResponse} from './gen/types/AuthenticationConfigurationResponse';
import type {CloudConfigurationResponse} from './gen/types/CloudConfigurationResponse';
import type {ComponentsConfigurationResponse} from './gen/types/ComponentsConfigurationResponse';
import type {DeploymentConfigurationResponse} from './gen/types/DeploymentConfigurationResponse';
import type {GetSystemConfigurationStatus200} from './gen/types/GetSystemConfiguration';
import type {JobMetricsConfigurationResponse} from './gen/types/JobMetricsConfigurationResponse';
import type {SystemConfigurationResponse} from './gen/types/SystemConfigurationResponse';

const jobMetricsConfigurationSchema = jobMetricsConfigurationResponseSchema;
type JobMetricsConfiguration = JobMetricsConfigurationResponse;

const componentsConfigurationSchema = componentsConfigurationResponseSchema;
type ComponentsConfiguration = ComponentsConfigurationResponse;

const deploymentConfigurationSchema = deploymentConfigurationResponseSchema;
type DeploymentConfiguration = DeploymentConfigurationResponse;

const authenticationConfigurationSchema = authenticationConfigurationResponseSchema;
type AuthenticationConfiguration = AuthenticationConfigurationResponse;

const cloudConfigurationSchema = cloudConfigurationResponseSchema;
type CloudConfiguration = CloudConfigurationResponse;

const systemConfigurationSchema = systemConfigurationResponseSchema;
type SystemConfiguration = SystemConfigurationResponse;

const getSystemConfigurationResponseBodySchema = getSystemConfigurationStatus200Schema;
type GetSystemConfigurationResponseBody = GetSystemConfigurationStatus200;

const getSystemConfiguration = {
	method: 'GET',
	getUrl: () => `/${API_VERSION}/system/configuration` as const,
} as const satisfies Endpoint;

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
