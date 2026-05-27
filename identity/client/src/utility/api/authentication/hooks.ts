/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMutation, useQuery } from "@tanstack/react-query";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import { getAuthentication, getSaasUserToken } from ".";

export const useAuthentication = (options?: { enabled?: boolean }) =>
  useQuery({
    queryKey: queryKeys.authentication.me,
    queryFn: () => unwrap(getAuthentication(undefined)(getApiBaseUrl())),
    enabled: options?.enabled,
    meta: { skipErrorNotification: true },
  });

export const useSaasUserToken = () =>
  useMutation({
    mutationFn: () => unwrap(getSaasUserToken(undefined)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
  });
