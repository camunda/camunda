/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import io.camunda.auth.domain.model.AuthMappingRule;
import io.camunda.auth.domain.port.outbound.MappingRuleReadPort;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link MappingRuleReadPort} using MyBatis. */
public class RdbmsMappingRuleReadAdapter implements MappingRuleReadPort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsMappingRuleReadAdapter.class);

  private final MappingRuleMapper mapper;

  public RdbmsMappingRuleReadAdapter(final MappingRuleMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<AuthMappingRule> findById(final String mappingRuleId) {
    LOG.debug("Finding mapping rule by mappingRuleId={}", mappingRuleId);
    final MappingRuleEntity entity = mapper.findById(mappingRuleId);
    return Optional.ofNullable(entity).map(RdbmsMappingRuleReadAdapter::toDomain);
  }

  @Override
  public List<AuthMappingRule> findAll() {
    LOG.debug("Finding all mapping rules");
    return mapper.findAll().stream()
        .map(RdbmsMappingRuleReadAdapter::toDomain)
        .collect(Collectors.toList());
  }

  static AuthMappingRule toDomain(final MappingRuleEntity entity) {
    return new AuthMappingRule(
        entity.getMappingRuleKey(),
        entity.getMappingRuleId(),
        entity.getClaimName(),
        entity.getClaimValue(),
        entity.getName());
  }
}
