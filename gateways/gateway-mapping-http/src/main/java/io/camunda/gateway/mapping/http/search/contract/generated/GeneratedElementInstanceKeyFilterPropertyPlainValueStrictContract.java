/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.annotation.Generated;

/**
 * Wrapper for the plain-value branch of the ElementInstanceKeyFilterProperty oneOf. Represents a
 * direct value match (implicit $eq).
 */
@JsonDeserialize(using = JsonDeserializer.None.class)
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedElementInstanceKeyFilterPropertyPlainValueStrictContract(String value)
    implements GeneratedElementInstanceKeyFilterPropertyStrictContract {}
