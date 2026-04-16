/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MappingRuleFilter;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MappingRuleFilterMapper {

  private MappingRuleFilterMapper() {}

  public static MappingRuleFilter toMappingRuleFilter(
      final io.camunda.gateway.protocol.model.MappingRuleFilter filter) {
    if (filter == null) {
      return FilterBuilders.mappingRule().build();
    }
    final var builder = FilterBuilders.mappingRule();
    Optional.ofNullable(filter.getMappingRuleId()).ifPresent(builder::mappingRuleId);
    Optional.ofNullable(filter.getClaimName()).ifPresent(builder::claimName);
    Optional.ofNullable(filter.getClaimValue()).ifPresent(builder::claimValue);
    Optional.ofNullable(filter.getName()).ifPresent(builder::name);
    return builder.build();
  }
}
