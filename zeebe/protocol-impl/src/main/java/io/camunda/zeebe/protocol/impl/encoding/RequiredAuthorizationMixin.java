/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.core.auth.RequiredAuthorization;

// RequiredAuthorization lives in CSL annotation-free; this mixin provides the same snake_case
// wire format that the in-class Jackson annotations on the old Authorization<T> produced.
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties({"wildcard"})
abstract class RequiredAuthorizationMixin {

  static void addTo(final ObjectMapper mapper) {
    mapper.addMixIn(RequiredAuthorization.class, RequiredAuthorizationMixin.class);
  }

  @JsonProperty("resource_type")
  abstract io.camunda.security.api.model.authz.AuthorizationResourceType resourceType();

  @JsonProperty("permission_type")
  abstract io.camunda.security.api.model.authz.PermissionType permissionType();

  @JsonProperty("resource_ids")
  abstract java.util.List<String> resourceIds();

  @JsonProperty("resource_property_names")
  abstract java.util.Set<String> resourcePropertyNames();

  @JsonProperty("transitive")
  abstract boolean transitive();

  @JsonIgnore
  abstract java.util.function.Function<?, String> resourceIdSupplier();

  @JsonIgnore
  abstract java.util.function.Predicate<?> condition();
}
