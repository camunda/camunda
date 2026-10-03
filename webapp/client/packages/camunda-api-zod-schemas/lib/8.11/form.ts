/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';

const formResultSchema = z.object({
	tenantId: z.string(),
	formId: z.string(),
	schema: z.string(),
	version: z.number(),
	formKey: z.string(),
});
type FormResult = z.infer<typeof formResultSchema>;

const getForm = {
	method: 'GET',
	getUrl: ({formKey}) => `/${API_VERSION}/forms/${formKey}` as const,
} as const satisfies Endpoint<Pick<FormResult, 'formKey'>>;

const getLatestFormByFormId = {
	method: 'GET',
	getUrl: ({formId, tenantId}) =>
		`/${API_VERSION}/forms/${encodeURIComponent(formId)}/latest${tenantId === undefined ? '' : `?tenantId=${encodeURIComponent(tenantId)}`}` as const,
} as const satisfies Endpoint<{formId: string; tenantId?: string}>;

export {formResultSchema, getForm, getLatestFormByFormId};
export type {FormResult};
