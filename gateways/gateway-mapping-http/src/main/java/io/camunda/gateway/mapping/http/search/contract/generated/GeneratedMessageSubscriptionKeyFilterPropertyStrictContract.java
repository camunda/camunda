/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/messages.yaml#/components/schemas/MessageSubscriptionKeyFilterProperty
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.annotation.Generated;

@JsonDeserialize(using = GeneratedMessageSubscriptionKeyFilterPropertyDeserializer.class)
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public sealed interface GeneratedMessageSubscriptionKeyFilterPropertyStrictContract
    permits GeneratedAdvancedMessageSubscriptionKeyFilterStrictContract,
        GeneratedMessageSubscriptionKeyFilterPropertyPlainValueStrictContract {}
