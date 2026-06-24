/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { CreateUserRequestBody } from "@camunda/camunda-api-zod-schemas/8.10";
import { ApiDefinition, apiPost } from "src/utility/api/request";

export const SETUP_ENDPOINT = "/setup/user";

export const createAdminUser: ApiDefinition<
  undefined,
  CreateUserRequestBody
> = (user) => apiPost(SETUP_ENDPOINT, user);
