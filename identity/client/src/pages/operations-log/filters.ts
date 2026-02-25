/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  buildArrayParam,
  createQuerySync,
  parseArrayParam,
} from "src/utility/filters/queryFilters.ts";
import {
  AuditLogEntityType,
  AuditLogOperationType,
  AuditLogResult,
} from "@camunda/camunda-api-zod-schemas/8.9";

export type AuditLogFilters = {
  operationType: AuditLogOperationType[];
  entityType: AuditLogEntityType[];
  result: AuditLogResult | "all";
  actor: string;
  timestampFrom: string;
  timestampTo: string;
};

const auditLogQuerySync = createQuerySync<AuditLogFilters>({
  operationType: {
    parse: (params) =>
      parseArrayParam<AuditLogOperationType>(params.get("operationType")),
    serialize: (value, params) => {
      const v = buildArrayParam(value);
      if (v) params.set("operationType", v);
    },
  },

  entityType: {
    parse: (params) =>
      parseArrayParam<AuditLogEntityType>(params.get("entityType")),
    serialize: (value, params) => {
      const v = buildArrayParam(value);
      if (v) params.set("entityType", v);
    },
  },

  result: {
    parse: (params) => (params.get("result") as AuditLogResult) ?? "all",
    serialize: (value, params) => {
      if (value !== "all") {
        params.set("result", value);
      }
    },
  },

  actor: {
    parse: (params) => params.get("actor") ?? "",
    serialize: (value, params) => {
      if (value) params.set("actor", value);
    },
  },

  timestampFrom: {
    parse: (params) => params.get("timestampFrom") ?? "",
    serialize: (value, params) => {
      if (value) {
        params.set("timestampFrom", value);
      }
    },
  },

  timestampTo: {
    parse: (params) => params.get("timestampTo") ?? "",
    serialize: (value, params) => {
      if (value) {
        params.set("timestampTo", value);
      }
    },
  },
});

export { auditLogQuerySync };
