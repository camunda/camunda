/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.annotation.Generated;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
  @JsonSubTypes.Type(GeneratedProcessInstanceCreationInstructionByKeyStrictContract.class),
  @JsonSubTypes.Type(GeneratedProcessInstanceCreationInstructionByIdStrictContract.class)
})
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public sealed interface GeneratedProcessInstanceCreationInstructionStrictContract
    permits GeneratedProcessInstanceCreationInstructionByKeyStrictContract,
        GeneratedProcessInstanceCreationInstructionByIdStrictContract {}
