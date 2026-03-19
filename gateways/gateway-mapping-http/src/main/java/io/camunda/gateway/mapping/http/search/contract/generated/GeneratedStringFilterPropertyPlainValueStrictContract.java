/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/filters.yaml#/components/schemas/StringFilterProperty
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.annotation.Generated;

/**
 * Wrapper for the plain-value branch of the StringFilterProperty oneOf. Represents a direct value
 * match (implicit $eq).
 */
@JsonDeserialize(using = JsonDeserializer.None.class)
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedStringFilterPropertyPlainValueStrictContract(String value)
    implements GeneratedStringFilterPropertyStrictContract {}
