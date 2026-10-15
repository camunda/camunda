/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.security.core.auth.MappingRuleMatcher;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MappingRuleEntity(
    String mappingRuleId,
    Long mappingRuleKey,
    String claimName,
    String claimValue,
    String name,
    String type,
    String origin)
    implements MappingRuleMatcher.MappingRule {

  public static final String TYPE_TOKEN_CLAIM = "TOKEN_CLAIM";
  public static final String TYPE_USER_ATTRIBUTE = "USER_ATTRIBUTE";
  public static final String TYPE_GROUP_ATTRIBUTE = "GROUP_ATTRIBUTE";

  public MappingRuleEntity(
      final String mappingRuleId,
      final Long mappingRuleKey,
      final String claimName,
      final String claimValue,
      final String name,
      final String type) {
    this(mappingRuleId, mappingRuleKey, claimName, claimValue, name, type, "");
  }

  public MappingRuleEntity(
      final String mappingRuleId,
      final Long mappingRuleKey,
      final String claimName,
      final String claimValue,
      final String name) {
    this(mappingRuleId, mappingRuleKey, claimName, claimValue, name, TYPE_TOKEN_CLAIM, "");
  }

  public MappingRuleEntity {
    Objects.requireNonNull(mappingRuleId, "mappingRuleId");
    Objects.requireNonNull(mappingRuleKey, "mappingRuleKey");
    Objects.requireNonNull(claimName, "claimName");
    Objects.requireNonNull(claimValue, "claimValue");
    Objects.requireNonNull(name, "name");
    type = type == null ? TYPE_TOKEN_CLAIM : type;
    origin = origin == null ? "" : origin;
  }
}
