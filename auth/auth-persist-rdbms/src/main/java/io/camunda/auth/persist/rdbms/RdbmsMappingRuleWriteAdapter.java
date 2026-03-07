/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import io.camunda.auth.domain.model.AuthMappingRule;
import io.camunda.auth.domain.port.outbound.MappingRuleWritePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link MappingRuleWritePort} using MyBatis. */
public class RdbmsMappingRuleWriteAdapter implements MappingRuleWritePort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsMappingRuleWriteAdapter.class);

  private final MappingRuleMapper mapper;

  public RdbmsMappingRuleWriteAdapter(final MappingRuleMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void save(final AuthMappingRule mappingRule) {
    LOG.debug("Saving mapping rule mappingRuleId={}", mappingRule.mappingRuleId());
    final MappingRuleEntity entity = toEntity(mappingRule);
    final int updated = mapper.update(entity);
    if (updated == 0) {
      mapper.insert(entity);
    }
  }

  @Override
  public void deleteById(final String mappingRuleId) {
    LOG.debug("Deleting mapping rule by mappingRuleId={}", mappingRuleId);
    mapper.deleteById(mappingRuleId);
  }

  private static MappingRuleEntity toEntity(final AuthMappingRule rule) {
    final MappingRuleEntity entity = new MappingRuleEntity();
    entity.setMappingRuleKey(rule.mappingRuleKey());
    entity.setMappingRuleId(rule.mappingRuleId());
    entity.setClaimName(rule.claimName());
    entity.setClaimValue(rule.claimValue());
    entity.setName(rule.name());
    return entity;
  }
}
