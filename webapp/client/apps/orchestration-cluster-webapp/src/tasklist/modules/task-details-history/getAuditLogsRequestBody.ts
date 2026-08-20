/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryUserTaskAuditLogsRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import type {TaskDetailsHistorySort} from './sortUtils';

const MAX_AUDIT_LOGS_PER_REQUEST = 50;

function getAuditLogsRequestBody(sort: TaskDetailsHistorySort): QueryUserTaskAuditLogsRequestBody {
	return {
		sort: [sort],
		filter: {result: 'SUCCESS'},
		page: {
			from: 0,
			limit: MAX_AUDIT_LOGS_PER_REQUEST,
		},
	};
}

export {getAuditLogsRequestBody};
