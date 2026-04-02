/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { CurrentUser } from "@camunda/camunda-api-zod-schemas/8.10";
import { ApiDefinition, apiGet } from "src/utility/api/request.ts";

export const getAuthentication: ApiDefinition<CurrentUser> = () =>
  apiGet("/authentication/me");

export const getSaasUserToken: ApiDefinition<string> = () =>
  apiGet("/authentication/me/token");
