/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  buildArrayParam,
  createSearchParamsSync,
  parseArrayParam,
} from "src/utility/filters/searchParamsFilters";
import {
  AuditLogEntityType,
  AuditLogOperationType,
  AuditLogResult,
  auditLogResultSchema,
} from "@camunda/camunda-api-zod-schemas/8.9";
import { isValidDate } from "src/utility/validate";

export type AuditLogFilters = {
  operationType: AuditLogOperationType[];
  entityType?: AuditLogEntityType;
  relatedEntityType?: AuditLogEntityType;
  relatedEntityKey: string;
  result: AuditLogResult | "all";
  actor: string;
  timestampFrom: string;
  timestampTo: string;
};

export const ALLOWED_OPERATION_TYPES = [
  "CREATE",
  "ASSIGN",
  "UNASSIGN",
  "DELETE",
  "UPDATE",
] as const satisfies readonly AuditLogOperationType[];

export const ALLOWED_ENTITY_TYPES = [
  "AUTHORIZATION",
  "ROLE",
  "USER",
  "GROUP",
  "MAPPING_RULE",
  "TENANT",
] as const satisfies readonly AuditLogEntityType[];

export const ALLOWED_RESULT_TYPES = [
  "all",
  ...auditLogResultSchema.options,
] as const;

const auditLogSearchParamsSync = createSearchParamsSync<AuditLogFilters>({
  operationType: {
    parse: (params) => {
      return parseArrayParam<AuditLogOperationType>(
        params.get("operationType"),
      ).filter((item): item is (typeof ALLOWED_OPERATION_TYPES)[number] =>
        ALLOWED_OPERATION_TYPES.some((t) => t === item),
      );
    },
    serialize: (value, params) => {
      const v = buildArrayParam(value);
      if (v) params.set("operationType", v);
    },
  },

  entityType: {
    parse: (params) => {
      const entityType = params.get("entityType");
      return ALLOWED_ENTITY_TYPES.find((t) => t === entityType);
    },
    serialize: (value, params) => {
      if (value) params.set("entityType", value);
    },
  },

  relatedEntityType: {
    parse: (params) => {
      const relatedEntityType = params.get("relatedEntityType");
      return ALLOWED_ENTITY_TYPES.find((t) => t === relatedEntityType);
    },
    serialize: (value, params) => {
      if (value) params.set("relatedEntityType", value);
    },
  },

  relatedEntityKey: {
    parse: (params) => params.get("relatedEntityKey") ?? "",
    serialize: (value, params) => {
      if (value) params.set("relatedEntityKey", value);
    },
  },

  result: {
    parse: (params) => {
      const result = params.get("result");
      return ALLOWED_RESULT_TYPES.find((t) => t === result) ?? "all";
    },
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
    parse: (params) => {
      const timestampFrom = params.get("timestampFrom");

      if (timestampFrom && isValidDate(timestampFrom)) {
        return timestampFrom;
      }

      return "";
    },
    serialize: (value, params) => {
      if (value) {
        params.set("timestampFrom", value);
      }
    },
  },

  timestampTo: {
    parse: (params) => {
      const timestampTo = params.get("timestampTo");

      if (timestampTo && isValidDate(timestampTo)) {
        return timestampTo;
      }

      return "";
    },
    serialize: (value, params) => {
      if (value) {
        params.set("timestampTo", value);
      }
    },
  },
});

export { auditLogSearchParamsSync };
