/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The authorization a batch operation's items are checked against, as the gateway writes it into
 * {@link BatchOperationCreationRecord#getAuthorizationCheckBuffer()} and the engine reads it back.
 * Its names and their order are the wire format: they match what earlier versions wrote for the
 * security library's {@code RequiredAuthorization}, so brokers of both versions read each other's
 * records.
 */
public record BatchOperationAuthorizationCheck(
    @JsonProperty("resource_type") AuthorizationResourceType resourceType,
    @JsonProperty("permission_type") PermissionType permissionType,
    @JsonProperty("resource_ids") @Nullable List<String> resourceIds,
    @JsonProperty("resource_property_names") @Nullable Set<String> resourcePropertyNames,
    @JsonProperty("transitive") boolean transitive) {}
