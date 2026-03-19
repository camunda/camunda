#!/usr/bin/env python3
"""Create GeneratedProcessInstanceFilterStrictContract.java"""
import os

path = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src/main/java/io/camunda/gateway/mapping/http/search/contract/generated",
    "GeneratedProcessInstanceFilterStrictContract.java"
)

content = '''/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/ProcessInstanceFilter
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedProcessInstanceFilterStrictContract(
    @Nullable Object startDate,
    @Nullable Object endDate,
    @Nullable Object state,
    @Nullable Boolean hasIncident,
    @Nullable Object tenantId,
    java.util.@Nullable List<GeneratedVariableValueFilterPropertyStrictContract> variables,
    @Nullable Object processInstanceKey,
    @Nullable Object parentProcessInstanceKey,
    @Nullable Object parentElementInstanceKey,
    @Nullable Object batchOperationId,
    @Nullable Object errorMessage,
    @Nullable Boolean hasRetriesLeft,
    @Nullable Object elementInstanceState,
    @Nullable Object elementId,
    @Nullable Boolean hasElementInstanceIncident,
    @Nullable Object incidentErrorHashCode,
    java.util.@Nullable Set<String> tags,
    @Nullable Object businessId,
    @Nullable Object processDefinitionId,
    @Nullable Object processDefinitionName,
    @Nullable Object processDefinitionVersion,
    @Nullable Object processDefinitionVersionTag,
    @Nullable Object processDefinitionKey,
    @JsonProperty("$or")
    java.util.@Nullable List<GeneratedProcessInstanceFilterFieldsStrictContract> $or
) {}
'''

with open(path, 'w') as f:
    f.write(content)
print(f"Written {len(content)} bytes to {path}")
print(f"File exists: {os.path.exists(path)}")
