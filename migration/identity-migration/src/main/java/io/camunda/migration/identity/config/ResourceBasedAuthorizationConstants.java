/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.Set;

public final class ResourceBasedAuthorizationConstants {
  public static final Set<AuthorizationResourceType> RBA_IRRELEVANT_RESOURCE_TYPES =
      Set.of(
          AuthorizationResourceType.AUTHORIZATION,
          AuthorizationResourceType.COMPONENT,
          AuthorizationResourceType.DOCUMENT,
          AuthorizationResourceType.GROUP,
          AuthorizationResourceType.ROLE,
          AuthorizationResourceType.USER,
          AuthorizationResourceType.TENANT,
          AuthorizationResourceType.MAPPING_RULE,
          AuthorizationResourceType.SYSTEM);

  private ResourceBasedAuthorizationConstants() {}
}
