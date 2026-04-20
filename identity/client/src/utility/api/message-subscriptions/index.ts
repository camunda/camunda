/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  QueryMessageSubscriptionsRequestBody,
  QueryMessageSubscriptionsResponseBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { ApiDefinition, apiPost } from "src/utility/api/request";

export const MESSAGE_SUBSCRIPTIONS_ENDPOINT = "/message-subscriptions";

export const searchMessageSubscriptions: ApiDefinition<
  QueryMessageSubscriptionsResponseBody,
  QueryMessageSubscriptionsRequestBody | undefined
> = (params) => apiPost(`${MESSAGE_SUBSCRIPTIONS_ENDPOINT}/search`, params);
