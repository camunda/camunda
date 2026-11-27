/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.access;

import static io.camunda.optimize.service.db.schema.index.AuthorizationIndex.ID;
import static io.camunda.optimize.service.db.schema.index.AuthorizationIndex.OWNER_ID;
import static io.camunda.optimize.service.db.schema.index.AuthorizationIndex.OWNER_TYPE;
import static io.camunda.optimize.service.db.schema.index.AuthorizationIndex.PERMISSION_TYPES;
import static io.camunda.optimize.service.db.schema.index.AuthorizationIndex.RESOURCE_ID;
import static io.camunda.optimize.service.db.schema.index.AuthorizationIndex.RESOURCE_MATCHER;
import static io.camunda.optimize.service.db.schema.index.AuthorizationIndex.RESOURCE_PROPERTY_NAME;
import static io.camunda.optimize.service.db.schema.index.AuthorizationIndex.RESOURCE_TYPE;

import io.camunda.security.api.model.authz.AuthorizationOwnerType;
import io.camunda.security.api.model.authz.AuthorizationResourceMatcher;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record AuthorizationDto(
    String id,
    AuthorizationOwnerType ownerType,
    String ownerId,
    AuthorizationResourceType resourceType,
    AuthorizationResourceMatcher resourceMatcher,
    String resourceId,
    String resourcePropertyName,
    Set<PermissionType> permissionTypes) {

  public Map<String, Object> toDocument() {
    final Map<String, Object> document = new HashMap<>();
    document.put(ID, id);
    document.put(OWNER_TYPE, ownerType.name());
    document.put(OWNER_ID, ownerId);
    document.put(RESOURCE_TYPE, resourceType.name());
    // stored as a number, like the Orchestration Cluster does
    document.put(RESOURCE_MATCHER, resourceMatcher.value());
    document.put(RESOURCE_ID, resourceId);
    document.put(RESOURCE_PROPERTY_NAME, resourcePropertyName);
    document.put(PERMISSION_TYPES, permissionTypes.stream().map(PermissionType::name).toList());
    return document;
  }

  public static AuthorizationDto fromDocument(final Map<String, Object> document) {
    return new AuthorizationDto(
        (String) document.get(ID),
        AuthorizationOwnerType.valueOf((String) document.get(OWNER_TYPE)),
        (String) document.get(OWNER_ID),
        AuthorizationResourceType.valueOf((String) document.get(RESOURCE_TYPE)),
        AuthorizationResourceMatcher.from(((Number) document.get(RESOURCE_MATCHER)).shortValue()),
        (String) document.get(RESOURCE_ID),
        (String) document.get(RESOURCE_PROPERTY_NAME),
        ((Collection<?>) document.get(PERMISSION_TYPES))
            .stream()
                .map(permission -> PermissionType.valueOf((String) permission))
                .collect(Collectors.toUnmodifiableSet()));
  }
}
