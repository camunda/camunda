/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  MappingRule,
  QueryMappingRulesRequestBody,
  UpdateMappingRuleRequestBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import {
  createMappingRule,
  deleteMappingRule,
  searchMappingRule,
  updateMappingRule,
} from ".";

export const useSearchMappingRules = (
  params?: QueryMappingRulesRequestBody,
  options?: { enabled?: boolean },
) =>
  useQuery({
    queryKey: queryKeys.mappingRules.search(params),
    queryFn: () => unwrap(searchMappingRule(params)(getApiBaseUrl())),
    enabled: options?.enabled,
  });

export const useCreateMappingRule = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: MappingRule) =>
      unwrap(createMappingRule(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.mappingRules.all }),
  });
};

export const useUpdateMappingRule = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (
      body: UpdateMappingRuleRequestBody & Pick<MappingRule, "mappingRuleId">,
    ) => unwrap(updateMappingRule(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.mappingRules.all }),
  });
};

export const useDeleteMappingRule = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<MappingRule, "mappingRuleId">) =>
      unwrap(deleteMappingRule(body)(getApiBaseUrl())),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.mappingRules.all }),
  });
};
