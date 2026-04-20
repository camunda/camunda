/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  MessageSubscription as BaseMessageSubscription,
  QueryMessageSubscriptionsRequestBody as BaseQueryMessageSubscriptionsRequestBody,
  QueryMessageSubscriptionsResponseBody as BaseQueryMessageSubscriptionsResponseBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { ApiDefinition, apiPost } from "src/utility/api/request";

export const MESSAGE_SUBSCRIPTIONS_ENDPOINT = "/message-subscriptions";

export const searchMessageSubscriptions: ApiDefinition<
  QueryMessageSubscriptionsResponseBody,
  QueryMessageSubscriptionsRequestBody | undefined
> = (params) => apiPost(`${MESSAGE_SUBSCRIPTIONS_ENDPOINT}/search`, params);

// TODO: Remove extended types once API is stabilized and schema merged back into @camunda/camunda-api-zod-schemas.
// https://github.com/camunda/camunda/issues/51241

type MessageSubscriptionType = "START_EVENT" | "PROCESS_EVENT";

export interface MessageSubscription extends BaseMessageSubscription {
  messageSubscriptionType: MessageSubscriptionType;
  extensionProperties: Record<string, string>;
  processDefinitionName: string | null;
  processDefinitionVersion: number | null;
}

type BaseMessageSubscriptionFilter = NonNullable<
  BaseQueryMessageSubscriptionsRequestBody["filter"]
>;

interface MessageSubscriptionFilter extends BaseMessageSubscriptionFilter {
  messageSubscriptionType?: MessageSubscriptionType;
}

export interface QueryMessageSubscriptionsRequestBody extends Omit<
  BaseQueryMessageSubscriptionsRequestBody,
  "filter"
> {
  filter?: MessageSubscriptionFilter;
}

export interface QueryMessageSubscriptionsResponseBody extends Omit<
  BaseQueryMessageSubscriptionsResponseBody,
  "items"
> {
  items: MessageSubscription[];
}
