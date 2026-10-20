/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import type {FormResult} from './gen/types/FormResult';

const getForm = {
	method: 'GET',
	getUrl: ({formKey}) => `/${API_VERSION}/forms/${formKey}` as const,
} as const satisfies Endpoint<Pick<FormResult, 'formKey'>>;

const getLatestFormByFormId = {
	method: 'GET',
	getUrl: ({formId, tenantId}) =>
		`/${API_VERSION}/forms/${encodeURIComponent(formId)}/latest${tenantId === undefined ? '' : `?tenantId=${encodeURIComponent(tenantId)}`}` as const,
} as const satisfies Endpoint<{formId: string; tenantId?: string}>;

export {getForm, getLatestFormByFormId};
