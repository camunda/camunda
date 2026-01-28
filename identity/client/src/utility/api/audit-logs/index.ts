/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ApiDefinition, apiPost } from "src/utility/api/request";
import type {
  QueryAuditLogsRequestBody,
  QueryAuditLogsResponseBody,
} from "@camunda/camunda-api-zod-schemas/8.9/audit-log";

export const AUDIT_LOGS_ENDPOINT = "/audit-logs";

export const searchAuditLogs: ApiDefinition<
  QueryAuditLogsResponseBody,
  QueryAuditLogsRequestBody | undefined
> = (params) => apiPost(`${AUDIT_LOGS_ENDPOINT}/search`, params);
