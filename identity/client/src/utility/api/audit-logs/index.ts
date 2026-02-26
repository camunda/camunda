/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { AuditLog } from "@camunda/camunda-api-zod-schemas/8.9";
import { ApiDefinition, apiPost } from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";

export type { AuditLog };

export const AUDIT_LOGS_ENDPOINT = "/audit-logs";

export const searchAuditLogs: ApiDefinition<
  SearchResponse<AuditLog>,
  Record<string, unknown> | undefined
> = (params) => apiPost(`${AUDIT_LOGS_ENDPOINT}/search`, params);
